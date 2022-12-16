package com.danawa.elasticsearch.index.analysis;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.elasticsearch.common.logging.Loggers;
import org.junit.Test;

import java.io.Reader;
import java.io.StringReader;

public class MeCabKoAnalyzerTest {

    private static Logger logger = Loggers.getLogger(MeCabKoAnalyzerTest.class, "");

    public class TestAnalyzer extends Analyzer {

        public TestAnalyzer() {
        }

        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            final Tokenizer tokenizer = new StandardTokenizer();
            TokenStream stream = new UpperCaseFilter(tokenizer);
            return new TokenStreamComponents(tokenizer, stream);
        }
    }

    @Test
    public void testSimpleSentence() throws Exception {
        String text = "아버지 가방 에 quickly 들어 가신다.";
        Analyzer analyzer = new TestAnalyzer();
        Reader reader = new StringReader(text);
        TokenStream stream = analyzer.tokenStream("", reader);
        CharTermAttribute termAttribute = stream.addAttribute(CharTermAttribute.class);
        OffsetAttribute offsetAttribute = stream.addAttribute(OffsetAttribute.class);

        //reset 을 한다.
        stream.reset();
        while(stream.incrementToken()) {
            logger.info("TOKEN:{} / {}~{}", termAttribute, offsetAttribute.startOffset(), offsetAttribute.endOffset());
        }
    }
}
