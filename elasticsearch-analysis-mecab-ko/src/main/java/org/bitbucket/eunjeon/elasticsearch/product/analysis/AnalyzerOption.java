package org.bitbucket.eunjeon.elasticsearch.product.analysis;

public class AnalyzerOption {
    private boolean useForQuery;
    private boolean useSynonym;
    private boolean useStopword;
    private boolean useFullString;
    private boolean toUppercase;

    public AnalyzerOption() {
        this(false, true, true, false, false);
    }

    public AnalyzerOption(boolean useForQuery, boolean useSynonym, boolean useStopword, boolean useFullString, boolean toUppercase) {
        this.useForQuery = useForQuery;
        this.useSynonym = useSynonym;
        this.useStopword = useStopword;
        this.useFullString = useFullString;
        this.toUppercase = toUppercase;
    }

    /**
     * 질의용 / 색인용 구분
     */
    public void useForQuery(boolean  forQuery) {
        this.useForQuery = forQuery;
    }

    public boolean useForQuery() {
        return this.useForQuery;
    }

    /**
     * 동의어 사용여부 구분
     */
    public boolean useSynonym() {
        return useSynonym;
    }

    public void useSynonym(boolean useSynonym) {
        this.useSynonym = useSynonym;
    }

    /**
     * 금지어 사용여부 구분
     * 금지어를 사용하지 않으면 금지어 추출시 STOPWORD 타입으로 추출됨
     */
    public boolean useStopword() {
        return useStopword;
    }

    public void useStopword(boolean useStopword) {
        this.useStopword = useStopword;
    }

    /**
     * 전체단어 사용여부 구분
     * 플러그인 검색을 사용하지 않는 경우 전체단어가 AND 검색조건에 포함되어
     * 검색결과가 나오지 않을수 있으므로 전체단어 출력여부를 선택하여 사용한다.
     */
    public boolean useFullString() {
        return useFullString;
    }

    public void useFullString(boolean useFullString) {
        this.useFullString = useFullString;
    }

    /**
     * 하이라이팅 시 매칭단어는 대/소문자 구분이 없어야 하므로
     * 대소문자를 가리지 않도록 대문자로만 추출여부 선택
     */
    public boolean toUppercase() {
        return toUppercase;
    }

    public void toUppercase(boolean toUppercase) {
        this.toUppercase = toUppercase;
    }

    @Override public String toString() {
        return "[" + useForQuery + "," + useSynonym + "," + useStopword + "," + useFullString + "," + toUppercase + "]";
    }
}