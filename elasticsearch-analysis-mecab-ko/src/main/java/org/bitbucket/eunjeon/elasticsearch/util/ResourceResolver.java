package org.bitbucket.eunjeon.elasticsearch.util;

import org.json.JSONObject;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

public class ResourceResolver {

    private static final String PREFIX_FILE = "file:/";
    private static final String SUFFIX_CLASS = ".class";
    private static final String SUFFIX_JAR = ".jar!/";
    private static final String SEPARATOR_PATH = "/";

    public static final File getResourceRoot(Class<?> cls) {
        File ret = null;
		URL url = cls.getResource(cls.getSimpleName() + SUFFIX_CLASS);
		String path = url.getFile();
		if (path.startsWith(PREFIX_FILE)) {
			path = path.substring(PREFIX_FILE.length());
		}
		int cutIndex = path.indexOf(SUFFIX_JAR);
		if (cutIndex != -1) {
			//in JAR
			path = path.substring(0, cutIndex + SUFFIX_JAR.length() - 2);
			cutIndex = path.lastIndexOf(SEPARATOR_PATH);
            path = path.substring(0, cutIndex);
			if (File.separatorChar == '/') {
				path = File.separatorChar + path;
			}
            ret = new File(path);
		} else {
			//in workspace
            String pkg = cls.getPackageName();
            path = path.substring(0, path.length() - pkg.length() - SEPARATOR_PATH.length()
                - cls.getSimpleName().length() - SUFFIX_CLASS.length());
			if (File.separatorChar == '/') {
				path = File.separatorChar + path;
			}
            ret = new File(path);
        }
        return ret;
    }

	public static final Properties readProperties(File file) {
		Properties ret = new Properties();
		Reader reader = null;
		try {
			reader = new FileReader(file);
			ret.load(reader);
		} catch (Exception e) {
			ret = null;
		} finally {
			try { reader.close(); } catch (Exception ignore) { }
		}
		return ret;
	}

	public static final JSONObject readYmlConfig(File file) {
		JSONObject ret = null;
		Reader reader = null;
		try {
			reader = new FileReader(file);
			Yaml yaml = new Yaml();
			ret = new JSONObject(yaml.loadAs(reader, Map.class));
		} catch (Exception e) {
			ret = null;
		} finally {
			try { reader.close(); } catch (Exception ignore) { }
		}
		return ret;
	}
}