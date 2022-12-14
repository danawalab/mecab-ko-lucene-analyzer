package com.danawa.analysis;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.*;
import org.bitbucket.eunjeon.mecab_ko_lucene_analyzer.MeCabKoTokenizer;
import org.bitbucket.eunjeon.mecab_ko_lucene_analyzer.StandardPosAppender;
import org.bitbucket.eunjeon.mecab_ko_lucene_analyzer.TokenizerOption;
import org.bitbucket.eunjeon.mecab_ko_lucene_analyzer.tokenattributes.PartOfSpeechAttribute;
import org.bitbucket.eunjeon.mecab_ko_lucene_analyzer.tokenattributes.SemanticClassAttribute;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

public class MeCabKoTest {

    private TokenizerOption option = new TokenizerOption();

    private String tokenizerToString(Tokenizer tokenizer) throws Exception {
        OffsetAttribute extOffset = tokenizer.addAttribute(OffsetAttribute.class);
        PositionIncrementAttribute posIncrAtt =
                tokenizer.addAttribute(PositionIncrementAttribute.class);
        PositionLengthAttribute posLengthAtt =
                tokenizer.addAttribute(PositionLengthAttribute.class);
        CharTermAttribute term = tokenizer.addAttribute(CharTermAttribute.class);
        TypeAttribute type = tokenizer.addAttribute(TypeAttribute.class);
        SemanticClassAttribute semanticClass =
                tokenizer.addAttribute(SemanticClassAttribute.class);
        PartOfSpeechAttribute pos =
                tokenizer.addAttribute(PartOfSpeechAttribute.class);


        StringBuilder result = new StringBuilder();
        tokenizer.reset();
        while (tokenizer.incrementToken() == true) {
            result.append(new String(term.buffer(), 0, term.length())).append(":");
            result.append(type.type()).append(":");
            result.append(pos.partOfSpeech()).append(":");
            result.append(semanticClass.semanticClass()).append(":");
            result.append(String.valueOf(posIncrAtt.getPositionIncrement())).append(":");
            result.append(String.valueOf(posLengthAtt.getPositionLength())).append(":");
            result.append(String.valueOf(extOffset.startOffset())).append(":");
            result.append(String.valueOf(extOffset.endOffset()));
            result.append(",");
        }
        tokenizer.end();
        return result.toString();
    }

    private Tokenizer createTokenizer(
            StringReader reader, int compoundNounMinLength) throws IOException {
        option.compoundNounMinLength = compoundNounMinLength;
        Tokenizer tokenizer = new MeCabKoTokenizer(
                option,
                new StandardPosAppender(option));
        tokenizer.setReader(reader);
        return tokenizer;
    }

    @Test
    public void testSimpleSentence() throws Exception {
        String text = "학생용 핸드폰";
        Tokenizer tokenizer = createTokenizer(new StringReader(text), 3);
        System.out.println(text + " > " +  tokenizerToString(tokenizer));
        //학생용:NNP:NNP:null:1:1:0:3,핸드:NNG:NNG:null:1:1:4:6,핸드폰:COMPOUND:null:null:0:2:4:7,폰:NNG:NNG:null:1:1:6:7,
    }
}
