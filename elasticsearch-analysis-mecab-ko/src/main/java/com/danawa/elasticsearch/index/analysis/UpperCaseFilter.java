package com.danawa.elasticsearch.index.analysis;

import org.apache.lucene.analysis.CharacterUtils;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import java.io.IOException;

public final class UpperCaseFilter extends TokenFilter {
    private final CharTermAttribute termAtt = (CharTermAttribute)this.addAttribute(CharTermAttribute.class);

    public UpperCaseFilter(TokenStream in) {
        super(in);
    }

    public final boolean incrementToken() throws IOException {
        if (this.input.incrementToken()) {
            CharacterUtils.toUpperCase(this.termAtt.buffer(), 0, this.termAtt.length());
            return true;
        } else {
            return false;
        }
    }
}
