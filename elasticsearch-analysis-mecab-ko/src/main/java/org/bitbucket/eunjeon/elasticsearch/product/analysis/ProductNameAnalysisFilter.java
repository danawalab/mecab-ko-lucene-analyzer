package org.bitbucket.eunjeon.elasticsearch.product.analysis;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.bitbucket.eunjeon.elasticsearch.dict.analysis.ProductNameDictionary;
import org.bitbucket.eunjeon.elasticsearch.dict.analysis.SetDictionary;
import org.bitbucket.eunjeon.elasticsearch.dict.analysis.SpaceDictionary;
import org.bitbucket.eunjeon.elasticsearch.dict.analysis.SynonymDictionary;
import org.bitbucket.eunjeon.elasticsearch.dict.korean.KoreanWordExtractor;
import org.bitbucket.eunjeon.elasticsearch.dict.korean.PosTagProbEntry.PosTag;

import org.bitbucket.eunjeon.elasticsearch.product.analysis.ProductNameParsingRule.RuleEntry;
import org.bitbucket.eunjeon.elasticsearch.util.CharVector;
import org.elasticsearch.common.logging.Loggers;
import tokenattributes.ExtraTermAttribute;
import tokenattributes.SynonymAttribute;
import tokenattributes.TokenInfoAttribute;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.bitbucket.eunjeon.elasticsearch.product.analysis.ProductNameTokenizer.*;

public class ProductNameAnalysisFilter extends TokenFilter {

    private static final Logger logger = Loggers.getLogger(ProductNameAnalysisFilter.class, "");

    private final CharTermAttribute termAttribute = addAttribute(CharTermAttribute.class);
    private final TokenInfoAttribute tokenAttribute = addAttribute(TokenInfoAttribute.class);
    private final OffsetAttribute offsetAttribute = addAttribute(OffsetAttribute.class);
    private final ExtraTermAttribute extraTermAttribute = addAttribute(ExtraTermAttribute.class);
    private final SynonymAttribute synonymAttribute = addAttribute(SynonymAttribute.class);
    private final TypeAttribute typeAttribute = addAttribute(TypeAttribute.class);
    private final PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);
    private AnalyzerOption option;

    private KoreanWordExtractor extractor;

    private SynonymDictionary synonymDictionary;
    private SpaceDictionary spaceDictionary;
    private SetDictionary stopDictionary;
    private ProductNameDictionary dictionary;

    private ProductNameParsingRule parsingRule;
    private CharVector token;
    private List<RuleEntry> termList;

    // 이전 텀과 상대적인 거리. 1이면 한단어거리. 0이면 유사어등 같은 위치를 나타냄.
    private int positionIncrement = 0;

    public ProductNameAnalysisFilter(TokenStream input) {
        super(input);
    }

    public ProductNameAnalysisFilter(TokenStream input, ProductNameDictionary dictionary, AnalyzerOption option) {
        super(input);
        if (dictionary != null) {
            this.dictionary = dictionary;
            this.synonymDictionary = dictionary.getDictionary(ProductNameDictionary.DICT_SYNONYM, SynonymDictionary.class);
            this.spaceDictionary = dictionary.getDictionary(ProductNameDictionary.DICT_SPACE, SpaceDictionary.class);
            this.stopDictionary = dictionary.getDictionary(ProductNameDictionary.DICT_STOP, SetDictionary.class);
        }
        this.extractor = new KoreanWordExtractor(dictionary);
        this.option = option;
        extraTermAttribute.init(this);
        termList = new ArrayList<>();
        super.clearAttributes();
        logger.trace("init");
    }

    public final boolean incrementToken() throws IOException {
        boolean ret = false;
        // FIXME : 큐 마지막에 ASCII 텀이 남아 있다면 모델명규칙 등을 위해 남겨 두어야 함.
        // INFO : 텀 오프셋 불일치를 막기 위해 절대값을 사용 (버퍼 상대값은 되도록 사용하지 않음)
        if (parsingRule == null) {
            parsingRule = new ProductNameParsingRule(extractor, dictionary, option);
        }
        synonymAttribute.setSynonyms(null);
        extraTermAttribute.init(this);

        while (true) {
            if (termList.size() == 0) {
                if (tokenAttribute.isState(TokenInfoAttribute.STATE_INPUT_FINISHED)) {
                    ret = false;
                    break;
                }
                while (input.incrementToken()) {
                    // 한번 읽어온 버퍼를 다 소진할때까지 큐에 넣는다.
                    CharVector ref = tokenAttribute.ref();
                    String type = typeAttribute.type();
                    PosTag posTag = tokenAttribute.posTag();

                    // 색인시에는 전체텀을 추출하지 않는다.
                    if (!(option.useForQuery() && option.useFullString()) && FULL_STRING.equals(type)) {
                        continue;
                    }
                    ProductNameParsingRule.addEntry(termList, ref, type, posTag,
                            offsetAttribute.startOffset(), offsetAttribute.endOffset(), spaceDictionary);
                    if (tokenAttribute.isState(TokenInfoAttribute.STATE_INPUT_BUFFER_EXHAUSTED)) {
                        break;
                    }
                } // LOOP (incrementToken())
                // RULE PROCESS
                if (termList.size() > 0) {
                    parsingRule.init(termList);
                    parsingRule.processRule(termList, true);
                }
                logger.trace("ENTRY QUEUE-SIZE:{}", termList.size());
            } else {
                // 엔트리를 출력할때 오프셋 순서대로 정렬하여 출력한다.
                RuleEntry entry = null;
                while ((entry = termList.get(0)) == null) { termList.remove(0); }

                List<RuleEntry> subEntryList = entry.subEntry;
                CharSequence[] synonyms = entry.synonym;
                logger.trace("SUB:{} / {} / {}", entry, synonyms, subEntryList);

                // 분기. ES에서는 Synonym, ExtraTerm 등의 Attribute를 쓸수 없으므로
                // 색인시에는 동의어, 추가어를 일반텀으로 추출함.
                // 질의시에는 Synonym, ExtraTerm 속성으로 추출 하되
                // 따로 ES 에 질의 가능한 질의문을 생성하여 처리한다.
                // 색인시에는 오프셋이 앞으로 갈수 없으므로 긴 텀부터 순서대로 일반텀 추출한다

                if (option.useForQuery()) {
                    // 질의용
                    if(subEntryList != null) {
                        //질의어에서는 단위명의 숫자만을 뽑지는 않는다. (검색의 정확도를 위해)
                        //역으로는 뽑아야 한다. (1,204 를 검색할 경우 1,024gb 를 포함해야 하지만 1,024gb 를 검색했을 때 1,024 가 검색되지는 않는다.
                        if(entry.type == UNIT || entry.type == UNIT_ALPHA) {
                            for (int sinx = 0; sinx < subEntryList.size(); sinx++) {
                                if (subEntryList.get(sinx).type == NUMBER ||
                                        subEntryList.get(sinx).type == NUMBER_TRANS) {
                                    subEntryList.remove(sinx);
                                    sinx--;
                                }
                            }
                        }
                    }
                    testEntry(entry, null);
                    token = applyEntry(entry);
                    applySynonym(token, entry);
                    if (entry.subEntry != null && entry.subEntry.size() > 0) {
                        for (RuleEntry subEntry : entry.subEntry) {
                            List<CharSequence> synonymList = null;
                            CharVector cv = subEntry.makeTerm(null);
                            testEntry(subEntry, null);
                            if (option.useSynonym() && synonymDictionary.containsKey(cv)) {
                                synonymList = Arrays.asList(synonymDictionary.get(cv));
                                logger.trace("token:{} / synonym:{}", cv, synonymList);
                            }
                            extraTermAttribute.addExtraTerm(String.valueOf(cv), subEntry.type, synonymList);
                        }
                    }
                    termList.remove(0);
                    if (tokenAttribute.isState(TokenInfoAttribute.STATE_TERM_STOP)) {
                        logger.trace("STOP WORD:{}", token);
                        typeAttribute.setType(STOPWORD);
                        if (option.useStopword()) {
                            continue;
                        }
                    }
                    ret = true;
                    //positionIncrement증가.
                    positionIncrement = 1;
                    break;
                } else {
                    // 색인용
                    if (entry.buf != null) {
                        logger.trace("TERMLIST:{}", termList);
                        logger.trace("SUB:{}", entry.subEntry);
                        logger.trace("SYN:{}{}", "", entry.synonym);
                        testEntry(entry, null);
                        token = applyEntry(entry);
                        subEntryList = entry.subEntry;
                        if (synonyms != null) {
                            // 동의어 -> 일반텀으로 변경
                            if (subEntryList == null) {
                                entry.subEntry = subEntryList = new ArrayList<>();
                            }
                            for (int sinx = 0; sinx < synonyms.length; sinx++) {
                                CharVector cv = new CharVector(synonyms[sinx]);
                                subEntryList.add(sinx, new RuleEntry(cv.array(), 0, cv.length(),
                                        entry.startOffset, entry.endOffset, entry.type));
                            }
                            entry.synonym = synonyms = null;
                        }
                        if (subEntryList == null) {
                            termList.remove(0);
                        } else {
                            // 서브엔트리가 존재하는 경우 출력한 버퍼를 null 처리 하고 서브엔트리를 처리하도록 한다.
                            entry.buf = null;
                        }
                        if (tokenAttribute.isState(TokenInfoAttribute.STATE_TERM_STOP)) {
                            logger.trace("STOP WORD:{}", token);
                            typeAttribute.setType(STOPWORD);
                            if (option.useStopword()) {
                                continue;
                            }
                        }
                        ret = true;
                        //positionIncrement증가.
                        positionIncrement = 1;
                        break;
                    } else if (subEntryList.size() > 0) {
                        RuleEntry subEntry = subEntryList.get(0);
                        testEntry(subEntry, entry);
                        token = applyEntry(subEntry);
                        subEntryList.remove(0);
                        if (tokenAttribute.isState(TokenInfoAttribute.STATE_TERM_STOP)) {
                            logger.trace("STOP WORD:{}", token);
                            typeAttribute.setType(STOPWORD);
                            if (option.useStopword()) {
                                continue;
                            }
                        }
                        ret = true;
                        //positionIncrement 변화없음.
                        positionIncrement = 0;
                        break;
                    } else if (subEntryList.size() == 0) {
                        termList.remove(0);
                    }
                }
            }
        } // LOOP

        posIncrAtt.setPositionIncrement(positionIncrement);

        if (logger.isTraceEnabled()) {
            if (ret) {
                logger.trace("TERM:{} / {}~{} / {} ", termAttribute, offsetAttribute.startOffset(), offsetAttribute.endOffset());
            } else {
                logger.trace("FILTER STOPPED!!");
            }
        }
        return ret;
    }

    private void applySynonym(CharVector token, RuleEntry entry) {
        List<CharSequence> synonyms = new ArrayList<>();
        if (option.useSynonym() && option.useForQuery()) {
            if (synonymDictionary != null && synonymDictionary.containsKey(token)) {
                CharSequence[] wordSynonym = synonymDictionary.get(token);
                logger.trace("SYNONYM-FOUND:{}{}", "", wordSynonym);
                if (wordSynonym != null) {
                    synonyms.addAll(Arrays.asList(wordSynonym));
                }
                // 동의어는 한번 더 분석해 준다.
                // 단 단위명은 더 분석하지 않는다.
                if (typeAttribute.type() != UNIT) {
                    List<CharSequence> synonymsExt = parsingRule.synonymExtract(synonyms, entry);
                    if (synonymsExt != null) {
                        synonyms = synonymsExt;
                    }
                }
            }
        }
        // 본래 entry 에 있던 동의어는 이미 분석된 동의어 이므로 따로 처리할 필요가 없다.
        if (entry.synonym != null && option.useSynonym()) {
            synonyms.addAll(Arrays.asList(entry.synonym));
        }
        if (synonyms.size() > 0) {
            logger.trace("SET-SYNONYM:{}", synonyms);
            synonymAttribute.setSynonyms(synonyms);
        }
    }

    public void testEntry(RuleEntry entry, RuleEntry parent) {
        if ((parent == null || parent.type == MODEL_NAME) && entry.type == NUMBER && entry.length >= 5) {
            entry.type = MODEL_NAME;
        } else if (entry.type == UNIT_ALPHA) {
            entry.type = UNIT;
        } else if (entry.type == NUMBER_TRANS) {
            entry.type = NUMBER;
            CharVector entryStr = new CharVector(entry.makeTerm(null).toString().replace(",", ""));
            if (entryStr.length() != entry.length) {
                if (option.useForQuery()) {
                    if (entry.length != entryStr.length()) {
                        extraTermAttribute.addExtraTerm(entryStr.toString(), NUMBER, null);
                    }
                } else if (parent != null) {
                    List<RuleEntry> subEntryList = parent.subEntry;
                    // parent 가 존재한다면 subEntryList 가 null 일 이유는 없으므로
                    // 현재(2021.02.09)는 불필요 코드
                    if (subEntryList == null) {
                        subEntryList = new ArrayList<>();
                        parent.subEntry = subEntryList;
                    }
                    // 여기까지 정상적으로 타고 들어왔다면 subEntryList[0] = entry 임
                    if (subEntryList.get(0) == entry) {
                        subEntryList.add(1, new RuleEntry(entryStr.array(), entryStr.offset(), entryStr.length(),
                                entry.startOffset, entry.endOffset, entry.type));
                    }
                } else {
                    List<RuleEntry> subEntryList = entry.subEntry;
                    if (subEntryList == null) {
                        subEntryList = new ArrayList<>();
                        entry.subEntry = subEntryList;
                    }
                    subEntryList.add(new RuleEntry(entryStr.array(), entryStr.offset(), entryStr.length(),
                            entry.startOffset, entry.endOffset, entry.type));
                }
            }
        } else if (entry.type == null) {
            entry.type = UNCATEGORIZED;
        }
    }

    private CharVector applyEntry(RuleEntry entry) {
        CharVector ret;
        termAttribute.copyBuffer(entry.buf, entry.start, entry.length);
        offsetAttribute.setOffset(entry.startOffset, entry.endOffset);
        typeAttribute.setType(entry.type);
        if (option.toUppercase()) {
            toUppercase(termAttribute.buffer());
        }
        token = entry.makeTerm(null);
        if (stopDictionary != null && stopDictionary.set().contains(token)) {
            tokenAttribute.addState(TokenInfoAttribute.STATE_TERM_STOP);
        } else {
            tokenAttribute.rmState(TokenInfoAttribute.STATE_TERM_STOP);
        }
        ret = entry.makeTerm(null);
        return ret;
    }

    public void reset() throws IOException {
        super.reset();
        extraTermAttribute.init(this);
        parsingRule = null;
        token = null;
        termList = new ArrayList<>();
        this.clearAttributes();
    }

    public void toUppercase(char[] buffer) {
        for (int inx = 0; inx < buffer.length; inx++) {
            buffer[inx] = Character.toUpperCase(buffer[inx]);
        }
    }
}
