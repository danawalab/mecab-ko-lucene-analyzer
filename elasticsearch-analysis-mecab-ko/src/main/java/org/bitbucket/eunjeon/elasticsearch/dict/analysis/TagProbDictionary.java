package org.bitbucket.eunjeon.elasticsearch.dict.analysis;

import com.danawa.io.InputStreamDataInput;
import com.danawa.io.OutputStreamDataOutput;
import com.danawa.io.DataInput;
import com.danawa.io.DataOutput;
import org.apache.logging.log4j.Logger;
import org.bitbucket.eunjeon.elasticsearch.dict.korean.PosTagProbEntry.TagProb;
import org.bitbucket.eunjeon.elasticsearch.dict.korean.PosTagProbEntry.PosTag;
import org.bitbucket.eunjeon.elasticsearch.dict.korean.PreResult;
import org.bitbucket.eunjeon.elasticsearch.util.CharVector;
import org.elasticsearch.common.logging.Loggers;

import java.io.*;
import java.util.*;

public class TagProbDictionary implements Dictionary<TagProb, PreResult<CharSequence>>, ReadableDictionary, WritableDictionary {
    private static Logger logger = Loggers.getLogger(TagProbDictionary.class, "");
	private int seq;
    private String label;
	private boolean ignoreCase;
	private Map<CharSequence, List<TagProb>> probMap;
	private Map<CharSequence, PreResult<CharSequence>> preMap;

	public TagProbDictionary(boolean ignoreCase) {
		this.ignoreCase = ignoreCase;
		probMap = new HashMap<CharSequence, List<TagProb>>();
	}

	public TagProbDictionary(Map<CharSequence, List<TagProb>> probMap, boolean ignoreCase) {
		this.ignoreCase = ignoreCase;
		this.probMap = new HashMap<CharSequence, List<TagProb>>(probMap);
	}

	public TagProbDictionary(File file, boolean ignoreCase) {
		this.ignoreCase = ignoreCase;
		if (!file.exists()) {
			probMap = new HashMap<CharSequence, List<TagProb>>();
			logger.error("사전파일이 존재하지 않습니다. file={}", file.getAbsolutePath());
			return;
		}
		InputStream is = null;
		try {
			is = new FileInputStream(file);
			readFrom(is);
		} catch (IOException e) {
			logger.error("", e);
		} finally {
			try { is.close(); } catch (Exception ignore) { }
		}
	}

	public boolean ignoreCase() {
		return ignoreCase;
	}
	public String label() {
		return label;
	}
	public int seq() {
		return seq;
	}
	public void setPreMap(Map<CharSequence, PreResult<CharSequence>> preMap) {
		this.preMap = preMap;
	}

	public void appendNounEntry(Set<CharSequence> entrySet) {
		appendPosTagEntry(entrySet, PosTag.N, TagProb.MAX_PROB);
	}

	@Override
	public void appendAdditionalNounEntry(Set<CharSequence> entrySet, String tokenType) {

		if(entrySet == null) {
			return;
		}
		
		double prob = TagProb.getProb(tokenType);
		if (prob == -1) {
			// 입력하지 않았으면 추가하지 않는다.
			return;
		}
		appendPosTagEntry(entrySet, PosTag.N, prob);
	}

	public void appendPosTagEntry(Set<CharSequence> entrySet, PosTag posTag, double prob) {
		if(entrySet == null) {
			return;
		}
		
		Iterator<CharSequence> iterator = entrySet.iterator();
		TagProb tagProb = new TagProb(posTag, prob);
		while (iterator.hasNext()) {
			CharSequence key = iterator.next();
			putAndReplaceProbMap(key, tagProb);
		}
	}

	public void addSourceEntry(String line) {
		addSourceEntry(probMap, line, ignoreCase);
	}

	public static void addSourceEntry(
		Map<CharSequence, List<TagProb>> probMap,
		String line, boolean ignoreCase) {
		if (line.startsWith("#") || line.startsWith("//") || line.length() == 0) {
			return;
		}
		String[] tmp = line.split("\t");
		if (tmp.length != 3) { return; }

		PosTag posTag = null;
		if (tmp[1].startsWith("N")) {
			posTag = PosTag.N;
		} else if (tmp[1].startsWith("V")) {
			posTag = PosTag.V;
		} else if (tmp[1].startsWith("M")) {
			posTag = PosTag.M;
		} else if (tmp[1].startsWith("IC")) {
			posTag = PosTag.IC;
		} else if (tmp[1].startsWith("J")) {
			posTag = PosTag.J;
		} else if (tmp[1].startsWith("E")) {
			posTag = PosTag.E;
		} else if (tmp[1].startsWith("XPN")) {
			posTag = PosTag.XPN;
		} else {
			return;
		}
		
		CharVector key = new CharVector(tmp[0].trim());
		if (ignoreCase) {
			key.ignoreCase();
		}
		double prob = Double.parseDouble(tmp[2].trim());
		if (key.length() == 1) {
			// 한글자는 확률이 높아도 막는다.2014-2-3 swsong
			if (posTag == PosTag.N) {
				if (prob > -13) {
					prob = TagProb.MIN_PROB;
				}
			} else {
				// 명사가 아닌 한글자는 버린다.
				return;
			}
		}
		List<TagProb> tagProbList = probMap.get(key);
		TagProb tagProb = new TagProb(posTag, prob);
		if (tagProbList == null) {
			tagProbList = new ArrayList<TagProb>(1);
			probMap.put(key, tagProbList);
			tagProbList.add(tagProb);
		} else {
			if (tagProbList.contains(tagProb)) {
				for (int i = 0; i < tagProbList.size(); i++) {
					TagProb tagProb2 = tagProbList.get(i);
					if (tagProb2.posTag() == tagProb.posTag()) {
						if (tagProb2.prob() < tagProb.prob()) {
							tagProbList.remove(i);
							tagProbList.add(tagProb);
							break;
						}
					}
				}
			} else {
				tagProbList.add(tagProb);
			}
		}
	}

	private void putAndReplaceProbMap(CharSequence key, TagProb tagProb) {
		List<TagProb> tagProbList = probMap.get(key);
		if (tagProbList == null) {
			tagProbList = new ArrayList<TagProb>(1);
			tagProbList.add(tagProb);
			probMap.put(key, tagProbList);
		} else {
			boolean foundTag = false;
			for (int i = 0; i < tagProbList.size(); i++) {
				TagProb oldTagProb = tagProbList.get(i);
				if (oldTagProb.equals(tagProb)) {
					if (tagProb.prob() > oldTagProb.prob()) {
						// 새로 셋팅할 tagprob가 더 크면 재 셋팅필요.
						tagProbList.remove(i);
					} else {
						foundTag = true;
					}
					break;
				}
			}
			if (!foundTag) {
				tagProbList.add(tagProb);
			}
		}
	}

	// 최종사용시 수정불가 맵으로 변환하여 넘겨준다.
	public Map<CharSequence, List<TagProb>> getUnmodifiableDictionary() {
		return Collections.unmodifiableMap(probMap);
	}

	@Override
	public void readFrom(InputStream in) throws IOException {

		DataInput input = null;
		try {
			if (!(in instanceof BufferedInputStream)) {
				try { in = new BufferedInputStream(in); } catch (Exception ignore) { }
			}
			input = new InputStreamDataInput(in);
			probMap = new HashMap<CharSequence, List<TagProb>>();

			int size = input.readInt();
	
			for (int entryInx = 0; entryInx < size; entryInx++) {
				CharSequence key = new CharVector(input.readString());
	
				int probLength = input.readInt();
	
				List<TagProb> probs = new ArrayList<TagProb>(probLength);
	
				for (int proInx = 0; proInx < probLength; proInx++) {
					probs.add(new TagProb(PosTag.valueOf(input.readString()), input.readDouble()));
				}
	
				probMap.put(key, probs);
			}
		} finally {
			if(input!=null) try {
				input.close();
			} catch (IOException ignore) { }
		}
	}

	@Override
	public void writeTo(OutputStream out) throws IOException {
		DataOutput output = null;
		try {
			output = new OutputStreamDataOutput(out);
			//write size of map
			output.writeInt(probMap.size());
			Iterator<Map.Entry<CharSequence, List<TagProb>>> entrySet = probMap.entrySet().iterator();
			//write key and value map
			while(entrySet.hasNext()) {
				//write key
				Map.Entry<CharSequence, List<TagProb>> entry = entrySet.next();
				output.writeString(entry.getKey().toString());

				//write values
				List<TagProb> tagProbs = entry.getValue();
				output.writeInt(tagProbs.size());
				for(TagProb tagProb : tagProbs) {
					output.writeString(tagProb.posTag().toString());
					output.writeDouble(tagProb.prob());
				}
			}
		} finally {
			if(output!=null) try {
				output.close();
			} catch (IOException ignore) { }
		}
	}

	@Override
	public List<TagProb> find(CharSequence token) {
		return probMap.get(token);
	}

	@Override
	public PreResult<CharSequence> findPreResult(CharSequence token) {
		if (preMap != null) {
			return preMap.get(token);
		}
		return null;
	}

	@Override
	public int size() {
		return probMap.size();
	}

	@Override
	public void setPreDictionary(Map<CharSequence, PreResult<CharSequence>> map) {
		this.preMap = map;
	}

	public int getSeq() {
		return seq;
	}

	public void setSeq(int seq) {
		this.seq = seq;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}
}
