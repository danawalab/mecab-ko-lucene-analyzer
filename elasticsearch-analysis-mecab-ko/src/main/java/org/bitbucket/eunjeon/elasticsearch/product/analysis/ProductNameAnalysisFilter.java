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

    // ?????? ?????? ???????????? ??????. 1?????? ???????????????. 0?????? ???????????? ?????? ????????? ?????????.
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
        // FIXME : ??? ???????????? ASCII ?????? ?????? ????????? ??????????????? ?????? ?????? ?????? ????????? ???.
        // INFO : ??? ????????? ???????????? ?????? ?????? ???????????? ?????? (?????? ???????????? ????????? ???????????? ??????)
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
                    // ?????? ????????? ????????? ??? ?????????????????? ?????? ?????????.
                    CharVector ref = tokenAttribute.ref();
                    String type = typeAttribute.type();
                    PosTag posTag = tokenAttribute.posTag();

                    // ??????????????? ???????????? ???????????? ?????????.
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
                // ???????????? ???????????? ????????? ???????????? ???????????? ????????????.
                RuleEntry entry = null;
                while ((entry = termList.get(0)) == null) { termList.remove(0); }

                List<RuleEntry> subEntryList = entry.subEntry;
                CharSequence[] synonyms = entry.synonym;
                logger.trace("SUB:{} / {} / {}", entry, synonyms, subEntryList);

                // ??????. ES????????? Synonym, ExtraTerm ?????? Attribute??? ?????? ????????????
                // ??????????????? ?????????, ???????????? ??????????????? ?????????.
                // ??????????????? Synonym, ExtraTerm ???????????? ?????? ??????
                // ?????? ES ??? ?????? ????????? ???????????? ???????????? ????????????.
                // ??????????????? ???????????? ????????? ?????? ???????????? ??? ????????? ???????????? ????????? ????????????

                if (option.useForQuery()) {
                    // ?????????
                    if(subEntryList != null) {
                        //?????????????????? ???????????? ???????????? ????????? ?????????. (????????? ???????????? ??????)
                        //???????????? ????????? ??????. (1,204 ??? ????????? ?????? 1,024gb ??? ???????????? ????????? 1,024gb ??? ???????????? ??? 1,024 ??? ??????????????? ?????????.
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
                    //positionIncrement??????.
                    positionIncrement = 1;
                    break;
                } else {
                    // ?????????
                    if (entry.buf != null) {
                        logger.trace("TERMLIST:{}", termList);
                        logger.trace("SUB:{}", entry.subEntry);
                        logger.trace("SYN:{}{}", "", entry.synonym);
                        testEntry(entry, null);
                        token = applyEntry(entry);
                        subEntryList = entry.subEntry;
                        if (synonyms != null) {
                            // ????????? -> ??????????????? ??????
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
                            // ?????????????????? ???????????? ?????? ????????? ????????? null ?????? ?????? ?????????????????? ??????????????? ??????.
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
                        //positionIncrement??????.
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
                        //positionIncrement ????????????.
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
                // ???????????? ?????? ??? ????????? ??????.
                // ??? ???????????? ??? ???????????? ?????????.
                if (typeAttribute.type() != UNIT) {
                    List<CharSequence> synonymsExt = parsingRule.synonymExtract(synonyms, entry);
                    if (synonymsExt != null) {
                        synonyms = synonymsExt;
                    }
                }
            }
        }
        // ?????? entry ??? ?????? ???????????? ?????? ????????? ????????? ????????? ?????? ????????? ????????? ??????.
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
                    // parent ??? ??????????????? subEntryList ??? null ??? ????????? ????????????
                    // ??????(2021.02.09)??? ????????? ??????
                    if (subEntryList == null) {
                        subEntryList = new ArrayList<>();
                        parent.subEntry = subEntryList;
                    }
                    // ???????????? ??????????????? ?????? ??????????????? subEntryList[0] = entry ???
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
