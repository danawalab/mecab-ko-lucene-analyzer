package tokenattributes;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.util.Attribute;

import java.util.Iterator;
import java.util.List;

public interface ExtraTermAttribute extends Attribute {
	
	public void init(TokenStream tokenStream);
	
	public void addExtraTerm(String extraTerm, String type, List<CharSequence> synonyms);

	public int size();

	public Iterator<String> iterator();
}
