package org.bitbucket.eunjeon.elasticsearch.index;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.SpecialPermission;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.logging.Loggers;

import java.io.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DanawaBulkTextIndexer extends Thread implements FileFilter {

	private static Logger logger = Loggers.getLogger(DanawaBulkTextIndexer.class, "");

	private String indexName;
	private String path;
	private String enc;
	private int flush;
	private NodeClient client;
	private boolean running; 
	private int count;
	List<File> files;
	private static final Pattern ptnHead = Pattern.compile("\\x5b[%]([a-zA-Z0-9_-]+)[%]\\x5d");

	public DanawaBulkTextIndexer(String indexName, String path, String enc, int flush, NodeClient client) {
		this.indexName = indexName;
		this.path = path;
		this.enc = enc;
		this.flush = flush;
		this.client = client;
	}

	public boolean running() {
		return running;
	}

	public int count() {
		return count;
	}

	@Override public void run() {
		SpecialPermission.check();
		AccessController.doPrivileged((PrivilegedAction<Integer>) () -> {
			running = true;
			count = 0;
			files = new ArrayList<>();
			String[] paths = path.split(",");
			for (String path : paths) {
				path = path.trim();
				File base = new File(path);
				if (!base.exists()) { 
					logger.debug("BASE FILE NOT FOUND : {}", base);
				} else {
					if (base.isDirectory()) {
						base.listFiles(this);
					} else {
						files.add(base);
					}
				}
			}

			if (files.size() > 0) {
				BufferedReader reader = null;
				InputStream istream = null;
				long time = System.currentTimeMillis();
				boolean isSourceFile = false;
				try {
					BulkRequestBuilder builder = null;
					Map<String, Object> source;
					
					builder = client.prepareBulk();
					for (File file : files) {
						if (!file.exists()) {
							logger.debug("FILE NOT FOUND : {}", file);
							continue;
						}
						isSourceFile = false;
						istream =  new FileInputStream(file);
						reader = new BufferedReader(new InputStreamReader(istream, String.valueOf(enc)));
						logger.debug("PARSING FILE..{}", file);
						for (String line; (line = reader.readLine()) != null; count++) {
							Matcher mat = ptnHead.matcher(line);
							String key = null;
							int offset = 0;
							source = new HashMap<>();
							while (mat.find()) {
								isSourceFile = true;
								if (key != null) {
									fieldValue(source, key, line.substring(offset, mat.start()));
								}
								key = mat.group(1);
								offset = mat.end();
							}
							if (isSourceFile) {
								fieldValue(source, key, line.substring(offset));

								builder.add(client.prepareIndex(String.valueOf(indexName), "_doc").setSource(source));
								if (count > 0 && count % flush == 0) {
									builder.execute().actionGet();
									builder = client.prepareBulk();
								}
								if (count > 0 && count % 100000 == 0) {
									logger.debug("{} ROWS FLUSHED! in {}ms", count, System.currentTimeMillis() - time);
								}
							} else {
								logger.debug("{} IS NOT SOURCEFILE", file);
								// 소스파일이 아니므로 바로 다음파일로.
								break;
							}
						}
						try { reader.close(); } catch (Exception ignore) { }
					}
					builder.execute().actionGet();
					logger.debug("TOTAL {} ROWS in {}ms", count, System.currentTimeMillis() - time);
				} catch (Exception e) {
					logger.error("", e);
				} finally {
					try { reader.close(); } catch (Exception ignore) { }
				}
			} else {
				logger.debug("THERE'S NO SOURCE FILE(S) FOUND");
			}
			running = false;
			return 0;
		});
	}

	private void fieldValue(Map<String, Object> source, String key , String value) throws Exception {
		if ("".equals(key)) {
		} else if ("REGISTERDATE".equals(key)) {
			if (value != null && !"".equals(value)) {
				SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
				source.put(key, sdf.parse(value));
			}
		} else {
			source.put(key, value);
		}
		logger.trace("ROW:{} / {}", key, value);
	}

	@Override public boolean accept(File file) {
		if (!file.exists()) { return false; }
		if (file.isDirectory()) {
			file.listFiles(this);
		} else if (file.isFile()) {
			files.add(file);
		}
		return false;
	}
}