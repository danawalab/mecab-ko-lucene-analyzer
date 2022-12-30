package org.bitbucket.eunjeon.elasticsearch.product.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.bitbucket.eunjeon.elasticsearch.dict.analysis.ProductNameDictionary;

public class ProductNameAnalyzer extends Analyzer {
    private ProductNameDictionary dictionary;
    private AnalyzerOption option;

    public ProductNameAnalyzer(ProductNameDictionary commonDictionary) {
        this(commonDictionary, null);
        option = new AnalyzerOption();
    }

    public ProductNameAnalyzer(ProductNameDictionary commonDictionary, AnalyzerOption option) {
        super();
        dictionary = commonDictionary;
        this.option = option;
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new ProductNameTokenizer(dictionary, false);
        TokenStream stream = tokenizer;
        stream = new ProductNameAnalysisFilter(stream, dictionary, option);
        return new TokenStreamComponents(tokenizer, stream);
    }
}
