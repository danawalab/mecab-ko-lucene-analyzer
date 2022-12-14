/*******************************************************************************
 * Copyright 2013 Yongwoon Lee, Yungho Yu
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.bitbucket.eunjeon.mecab_ko_lucene_analyzer;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringReader;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.*;
import org.bitbucket.eunjeon.mecab_ko_lucene_analyzer.tokenattributes.PartOfSpeechAttribute;
import org.bitbucket.eunjeon.mecab_ko_lucene_analyzer.tokenattributes.SemanticClassAttribute;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class MeCabKoStandardTokenizerTest {
  private TokenizerOption option;

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

  @Before
  public void setUp() throws Exception {
    option = new TokenizerOption();
  }

  @After
  public void tearDown() throws Exception {
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
  public void testEmptyQuery() throws Exception {
    Tokenizer tokenizer = createTokenizer(
        new StringReader(""), TokenGenerator.DEFAULT_COMPOUND_NOUN_MIN_LENGTH);
    tokenizer.reset();
    assertEquals(false, tokenizer.incrementToken());
    tokenizer.close();
  }
  
  @Test
  public void testEmptyMorphemes() throws Exception {
    Tokenizer tokenizer = createTokenizer(
        new StringReader("!@#$%^&*"),
        TokenGenerator.DEFAULT_COMPOUND_NOUN_MIN_LENGTH);
    tokenizer.reset();
    assertEquals(false, tokenizer.incrementToken());
    tokenizer.close();
  }
  
  @Test
  public void testSemanticClassSentence() throws Exception {
    Tokenizer tokenizer = createTokenizer(
        new StringReader("????????? ?????????"), 2);
    assertEquals(
        "?????????:NNP:NNP:??????:1:1:0:3,??????:NNP:NNP:??????:1:1:4:6,"
        + "?????????:COMPOUND:null:null:0:2:4:7,???:NNG:NNG:null:1:1:6:7,",
        tokenizerToString(tokenizer));
  }
  

  @Test
  public void testShortSentence() throws Exception {
    Tokenizer tokenizer = createTokenizer(
        new StringReader("????????? ????????? ????????????"), 2);
    assertEquals(
        "???:NNG:NNG:null:1:1:0:1,??????:NNG:NNG:null:1:1:1:3,"
        + "???:NNG:NNG:null:1:1:4:5,?????????:COMPOUND:null:null:0:2:4:7,"
        + "??????:NNG:NNG:null:1:1:5:7,????????????:NNG:NNG:null:1:1:8:12,",
        tokenizerToString(tokenizer));

    tokenizer.reset();
    tokenizer.setReader(new StringReader("?????? ??????????????? ???????????????."));
    assertEquals(
        "??????:NNG:NNG:null:1:1:0:2,??????:NNG:NNG:null:1:1:3:5,"
        + "?????????:COMPOUND:null:null:0:2:3:6,???:NNG:NNG:null:1:1:5:6,"
        + "??????:EOJEOL:NNG+JKS:null:1:1:6:8,???:NNG:NNG:null:0:1:6:7,"
        + "???????????????:EOJEOL:VV+EP+EF:null:1:1:9:14,???/VV:VV:VV:null:0:1:9:10,",
        tokenizerToString(tokenizer));
    tokenizer.close();
  }
  
  @Ignore
  public void testComplexSentence() throws Exception {
    Tokenizer tokenizer = createTokenizer(
        new StringReader(
            "???????????? ????????? ???????????? ?????? ???????????? ????????? ????????? ????????? " +
            "????????? ??? ?????? ????????????."),
        TokenGenerator.DEFAULT_COMPOUND_NOUN_MIN_LENGTH);
    assertEquals(
        "??????:MAG:MAG:????????????/????????????:1:1:0:2," +
        "??????:MAG:MAG:????????????/????????????:1:1:2:4,?????????:EOJEOL:VA+EC:null:1:1:5:8," +
        "????????????:EOJEOL:XR+XSA+ETM:null:1:1:9:13,??????:XR:XR:null:0:1:9:11," +
        "??????:NNG:NNG:null:1:1:14:16,????????????:EOJEOL:NNG+JKS:null:1:1:17:21," +
        "?????????:NNG:NNG:null:0:1:17:20,?????????:EOJEOL:NNG+JKO:null:1:1:22:25," +
        "??????:NNG:NNG:null:0:1:22:24,???:NNG:NNG:null:1:1:26:27," +
        "?????????:COMPOUND:null:null:0:2:26:29,??????:NNG:NNG:null:1:1:27:29," +
        "?????????:EOJEOL:VV+EP+EC:null:1:1:30:33," +
        "?????????:EOJEOL:MAG+JX:null:1:1:34:37," +
        "??????:MAG:MAG:????????????/????????????:0:1:34:36,???:MM:MM:~??????:1:1:38:39," +
        "??????:EOJEOL:NNG+JKS:null:1:1:40:42,???:NNG:NNG:null:0:1:40:41," +
        "????????????:INFLECT:VV+EF:null:1:1:43:47,",
        tokenizerToString(tokenizer));
    tokenizer.close();
  }
  
  @Test
  public void testHanEnglish() throws Exception {
    Tokenizer tokenizer = createTokenizer(
        new StringReader("??????win"),
        TokenGenerator.DEFAULT_COMPOUND_NOUN_MIN_LENGTH);
    assertEquals("??????:NNG:NNG:null:1:1:0:2,win:SL:SL:null:1:1:2:5,",
        tokenizerToString(tokenizer));
    tokenizer.close();
  }
  
  @Test
  public void testDecompound() throws Exception {
    Tokenizer tokenizer = createTokenizer(
        new StringReader("?????????"),
        TokenGenerator.DEFAULT_COMPOUND_NOUN_MIN_LENGTH);
    assertEquals(
        "??????:NNG:NNG:null:1:1:0:2,?????????:COMPOUND:null:null:0:2:0:3,???:NNG:NNG:null:1:1:2:3,",
        tokenizerToString(tokenizer));
    tokenizer.close();
    
    tokenizer = createTokenizer(
        new StringReader("????????????"),
        TokenGenerator.DEFAULT_COMPOUND_NOUN_MIN_LENGTH);
    assertEquals(
        "??????:NNG:NNG:null:1:1:0:2,????????????:COMPOUND:null:null:0:2:0:4,"
        + "??????:NNG:NNG:null:1:1:2:4,",
        tokenizerToString(tokenizer));
    tokenizer.close();
  }
  
  @Test
  public void testNoDecompound() throws Exception {
    Tokenizer tokenizer = createTokenizer(
        new StringReader("?????????"),
        TokenGenerator.NO_DECOMPOUND);
    assertEquals("?????????:COMPOUND:NNG:null:1:2:0:3,", tokenizerToString(tokenizer));
    tokenizer.close();
    
    tokenizer = createTokenizer(
        new StringReader("????????????"),
        TokenGenerator.NO_DECOMPOUND);
    assertEquals(
        "????????????:COMPOUND:NNG:null:1:2:0:4,", tokenizerToString(tokenizer));
    tokenizer.close();
  }
  
  @Test
  public void testPreanalysisSentence() throws Exception {
    Tokenizer tokenizer = createTokenizer(
        new StringReader("???????????? ??????????????? ??????????????????."),
        TokenGenerator.DEFAULT_COMPOUND_NOUN_MIN_LENGTH);
    assertEquals(
        "??????:NNG:NNG:null:1:1:0:2,???:NR:NR:null:1:1:2:3,???:NNG:NNG:null:1:1:3:4,"
        + "???????????????:EOJEOL:NNG+JX:null:1:1:5:10,????????????:NNG:NNG:null:0:1:5:9,"
        + "??????:NNG:NNG:null:1:1:11:13,????????????:EOJEOL:NNG+VCP+EF:null:1:1:13:17,"
        + "??????:NNG:NNG:null:0:1:13:15,",
        tokenizerToString(tokenizer));
    tokenizer.close();
  }
  
  @Test
  public void testUnknownSurface() throws Exception {
    Tokenizer tokenizer = createTokenizer(
        new StringReader("?????? ?????? ??????"),
        TokenGenerator.DEFAULT_COMPOUND_NOUN_MIN_LENGTH);
    assertEquals(
        "??????:UNKNOWN:UNKNOWN:null:1:1:0:2,??????:EOJEOL:VA+ETM:null:1:1:3:5,"
        + "???/VA:VA:VA:null:0:1:3:4,??????:NNG:NNG:null:1:1:6:8,",
        tokenizerToString(tokenizer));
    tokenizer.close();
  }
}
