package com.danawa.elasticsearch.index.analysis;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;

import java.io.IOException;

public class ProductNameFilter extends TokenFilter {

    public ProductNameFilter(TokenStream input) {
        super(input);
    }

    @Override
    public boolean incrementToken() throws IOException {
        while (input.incrementToken()) {


        }
        return false;
    }
}
