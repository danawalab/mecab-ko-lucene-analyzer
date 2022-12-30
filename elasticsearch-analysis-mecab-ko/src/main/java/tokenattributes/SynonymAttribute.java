package tokenattributes;

import org.apache.lucene.util.Attribute;

import java.util.List;

/**
 */
public interface SynonymAttribute extends Attribute {
	public void setSynonyms(List<CharSequence> synonym);
	public List<CharSequence> getSynonyms();
}
