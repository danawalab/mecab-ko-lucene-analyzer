/*******************************************************************************
 * Copyright 2013 Yongwoon Lee, Yungho Yu
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.bitbucket.eunjeon.mecab_ko_mecab_loader;

import org.apache.logging.log4j.Logger;
import org.chasen.mecab.Model;
import org.elasticsearch.common.logging.Loggers;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.WeakHashMap;

public final class MeCabLoader {
  private static Map<String, Model> models = new WeakHashMap<>();
  private static Logger logger = Loggers.getLogger(MeCabLoader.class, "");
  static {
    AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
      try {
        System.loadLibrary("MeCab");
      } catch (UnsatisfiedLinkError e) {
//        System.err.println(
//                "Cannot load the native code.\n"
//                        + "Make sure your LD_LIBRARY_PATH contains MeCab.so path.\n" + e);
//        System.exit(1);
        throw new UnsatisfiedLinkError(
            "Cannot load the native code.\n"
                + "Make sure your LD_LIBRARY_PATH contains MeCab.so path.\n" + e);
      }
      return null;
    });
  }

  public static synchronized Model getModel(String args) throws RuntimeException {
    Model currentModel = models.get(args);

    if (currentModel == null) {
      currentModel = new Model(args);
      models.put(args, currentModel);
    } else {
      Model latestModel = new Model(args);
      Long currentSize = currentModel.dictionary_info().getSize();
      Long latestSize = latestModel.dictionary_info().getSize();

      // 사전 크기가 변경되었다면 모델을 최신으로 교체한다
      if(latestSize != 0){
        if(!currentSize.equals(latestSize)){
          logger.info("Compare Dictionary Size Current Dic. {} vs Latest Dic. {} ", currentSize, latestSize);
          logger.info("Swap Dictionary ... ");
          currentModel.swap(latestModel);
          models.put(args, currentModel);
        }
      }
    }
    return currentModel;
  }

  public static int getModelCount() {
    return models.size();
  }
}
