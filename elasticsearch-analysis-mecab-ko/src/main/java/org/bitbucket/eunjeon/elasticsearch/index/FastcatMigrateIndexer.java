package org.bitbucket.eunjeon.elasticsearch.index;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.SpecialPermission;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.logging.Loggers;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

public class FastcatMigrateIndexer extends Thread {

	private static Logger logger = Loggers.getLogger(FastcatMigrateIndexer.class, "");

	private final static String NEWLINE = "\r\n";

	private String indexName;
	private String url;
	private int start;
	private int length;
	private String path;
	private String enc;
	private NodeClient client;
	private int flush;
	private boolean running;
	private int count;

	public FastcatMigrateIndexer(String url, int start, int length, String path, String enc, String indexName, int flush, NodeClient client) {
		if (start < 1) {
			start = 1;
		}
		if (length < 10) {
			length = 10;
		}
		if (path != null && !"".equals(path) && (enc == null || "".equals(enc))) {
			enc = "utf-8";
		}
		this.url = url;
		this.start = start;
		this.length = length;
		this.path = path;
		this.enc = enc;
		this.indexName = indexName;
		this.flush = flush;
		this.client = client;
	}

	public boolean running() {
		return running;
	}

	public int count() {
		return count;
	}

	public void migrateFastcat() {
		HttpURLConnection con = null;
		BufferedReader reader = null;
		BufferedWriter writer = null;
		File file = null;
		BulkRequestBuilder builder = null;
		long time = System.currentTimeMillis();
		try {
			logger.trace("SEND REQUEST {}", url);
			int total = start + length;

			if (path != null && !"".equals(path) && (file = new File(path)) != null && file.getParentFile().exists()) {
				writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), enc));
			}

			if (client != null) { builder = client.prepareBulk(); }

			for (int sinx = start; sinx < total; sinx += length) {
				con = (HttpURLConnection) new URL(url + "&sn=" + sinx + "&ln=" + length).openConnection();
				con.setRequestMethod("GET");
				int responseCode = con.getResponseCode();
				logger.trace("RESPONSE:{}", responseCode);
				if (responseCode == HttpURLConnection.HTTP_OK) {
					reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
					JSONTokener tokener = new JSONTokener(reader);
					JSONObject resp = new JSONObject(tokener);
					total = resp.optInt("total_count", total);
					List<Object> fieldNames = resp.optJSONArray("fieldname_list").toList();
					JSONArray list = resp.optJSONArray("result");

					for (int rinx = 0; rinx < list.length(); rinx++, count++) {
						JSONObject row = list.optJSONObject(rinx);
						if (writer != null) {
							for (Object key : fieldNames) {
								String keyStr = String.valueOf(key);
								String value = row.getString(keyStr).replaceAll("[\t\r\n\0]", " ");
								// logger.debug("KV:{} / {}", key, value);
								writer.append("[%").append(keyStr).append("%]").append(value);
							}
							writer.append(NEWLINE);
						}
						if (builder != null) {
							builder.add(client.prepareIndex(String.valueOf(indexName), "_doc")
								.setSource(filterMap(row.toMap())));
							if (count > 0 && count % flush == 0) {
								builder.execute().actionGet();
								builder = client.prepareBulk();
							}
							if (count > 0 && count % 100000 == 0) {
								logger.debug("{} ROWS FLUSHED! in {}ms", count, System.currentTimeMillis() - time);
							}
						}
					}
				}
			}
			if (builder != null) { builder.execute().actionGet(); }
			logger.debug("TOTAL {} ROWS in {}ms", count, System.currentTimeMillis() - time);
		} catch (Exception e) { 
			logger.error("", e);
		} finally {
			try { writer.close(); } catch (Exception ignore) { }
			try { reader.close(); } catch (Exception ignore) { }
		}
	}

	private Map<String, Object> filterMap(Map<String, Object> map) throws Exception {
		for (String key : map.keySet()) {
			fieldValue(map, key, String.valueOf(map.get(key)));
		}
		return map;
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

	@Override public void run() {
		SpecialPermission.check();
		AccessController.doPrivileged((PrivilegedAction<Integer>) () -> {
			running = true;
			count = 0;
			migrateFastcat();
			running = false;
			return 0;
		});
	}
}