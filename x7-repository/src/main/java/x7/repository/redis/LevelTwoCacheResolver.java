/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package x7.repository.redis;

import x7.core.config.Configs;
import x7.core.repository.CacheException;
import x7.core.repository.CacheResolver;
import x7.core.util.JsonX;
import x7.core.util.VerifyUtil;
import x7.core.web.Page;
import x7.repository.exception.PersistenceException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * 
 * Level two Cache
 * @author sim
 *
 */
public class LevelTwoCacheResolver implements CacheResolver {

	public final static String NANO_SECOND = ".N_S";
	
	private static LevelTwoCacheResolver instance = null;
	public static LevelTwoCacheResolver getInstance(){
		if (instance == null){
			instance = new LevelTwoCacheResolver();
		}
		return instance;
	}
	
	/**
	 * 标记缓存要更新
	 * @param clz
	 * @return nanuTime_String
	 */
	@SuppressWarnings("rawtypes")
	public String markForRefresh(Class clz){
		String key = getNSKey(clz);
		String time = String.valueOf(System.nanoTime());
		boolean flag = JedisConnector_Cache.getInstance().set(key.getBytes(), time.getBytes());
		if (!flag)
			throw new CacheException("markForRefresh failed");
		return time;
	}
	
	/**
	 * 
	 * FIXME {hash tag}
	 */
	@SuppressWarnings("rawtypes")
	public void remove(Class clz, String key){
		key = getSimpleKey(clz, key);
		boolean flag = JedisConnector_Cache.getInstance().delete(key.getBytes());
		if (!flag)
			throw new CacheException("remove failed");
	}

	public void remove(Class clz) {

		String key = getSimpleKey(clz);

		Set<String> keySet = JedisConnector_Cache.getInstance().keys(key);

		for (String k : keySet) {
			boolean flag = JedisConnector_Cache.getInstance().delete(k.getBytes());
			if (!flag)
				throw new CacheException("remove failed");
		}

	}
	
	@SuppressWarnings("rawtypes")
	private String getNSKey(Class clz){
		return clz.getName()+ NANO_SECOND;
	}
	
	@SuppressWarnings("unused")
	private String getNS(String nsKey){
		return JedisConnector_Cache.getInstance().get(nsKey);
	}
	
	@SuppressWarnings("rawtypes")
	private List<byte[]> getKeyList(Class clz, List<String> conditionList){
		if (conditionList == null || conditionList.isEmpty())
			return null;
		List<byte[]> keyList = new ArrayList<byte[]>();
		for (String condition : conditionList){
			String key = getSimpleKey(clz, condition);
			keyList.add(key.getBytes());
		}
		if (keyList.isEmpty())
			return null;
		List<byte[]> arrList= new ArrayList<byte[]>();
//		keyList.toArray(arrList);
		int i = 0;
		for (byte[] keyB : keyList){
			arrList.add(keyB);
		}
		return arrList;
	}
	
	/**
	 * FIXME 有简单simpleKey的地方全改成字符串存储, value为bytes, new String(bytes)
	 * @param clz
	 * @param condition
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	private String getSimpleKey(Class clz, String condition){
		return "{"+clz.getName()+"}." + condition;
	}

	private String getSimpleKey(Class clz){
		return "{"+clz.getName()+"}.*" ;
	}
	
	
	@SuppressWarnings("rawtypes")
	private String getKey(Class clz, Object conditionObj){
		String condition = JsonX.toJson(conditionObj);
		long startTime = System.currentTimeMillis();
		String key =  VerifyUtil.toMD5(getPrefix(clz) + condition);
		long endTime = System.currentTimeMillis();
		System.out.println("time_getKey = "+(endTime - startTime));
		return key;
	}

	
	/**
	 * 获取缓存KEY前缀
	 * @param clz
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	private String getPrefix(Class clz){
		String key = getNSKey(clz);
		byte[] nsArr = JedisConnector_Cache.getInstance().get(key.getBytes());
		if (nsArr == null){
			String str = markForRefresh(clz);
			return clz.getName() + str;
		}
		return clz.getName() + new String(nsArr);
	}

	/**
	 * FIXME {hash tag}
	 */
	@SuppressWarnings("rawtypes")
	@Override
	public void set(Class clz, String key, Object obj) {
		key = getSimpleKey(clz, key);
		int validSecond =  getValidSecondAdjusted();
		JedisConnector_Cache.getInstance().set(key.getBytes(), PersistenceUtil.toBytes(obj), validSecond);
	}

	
	private int getValidSecondAdjusted(){
		return  Configs.getIntValue("x7.cache.second") * 120;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void setResultKeyList(Class clz, Object condition, List<String> keyList) {
		String key = getKey(clz, condition);
		int validSecond = Configs.getIntValue("x7.cache.second");
		try{
			JedisConnector_Cache.getInstance().set(key.getBytes(), ObjectUtil.toBytes(keyList), validSecond);
		}catch (Exception e) {
			throw new PersistenceException(e.getMessage());
		}
	}
	
	@SuppressWarnings("rawtypes")
	@Override
	public <T> void setResultKeyListPaginated(Class<T> clz, Object condition, Page<T> pagination) {
		
		int validSecond = Configs.getIntValue("x7.cache.second");
		setResultKeyListPaginated(clz, condition, pagination, validSecond);
	}
	
	@Override
	public <T> void setResultKeyListPaginated(Class<T> clz, Object condition, Page<T> pagination, int second) {
		
		String key = getKey(clz, condition);
		try{
			JedisConnector_Cache.getInstance().set(key.getBytes(), ObjectUtil.toBytes(pagination), second);
		}catch (Exception e) {
			throw new PersistenceException(e.getMessage());
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public List<String> getResultKeyList(Class clz, Object condition) {
		String key = getKey(clz, condition);
		System.out.println("get key: " + key);
		long startTime = System.currentTimeMillis();
		byte[] bytes = JedisConnector_Cache.getInstance().get(key.getBytes());
		long endTime = System.currentTimeMillis();
		System.out.println("time_getResultKeyList = "+(endTime - startTime));
		if (bytes == null)
			return new ArrayList<String>();
		
		return ObjectUtil.toList(bytes, String.class);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Page<String> getResultKeyListPaginated(Class clz, Object condition) {
		String key = getKey(clz, condition);
		System.out.println("get key: " + key);
		byte[] bytes = JedisConnector_Cache.getInstance().get(key.getBytes());
		
		if (bytes == null)
			return null;
		
		return ObjectUtil.toPagination(bytes, String.class);
	}

	@Override
	public <T> List<T> list(Class<T> clz, List<String> keyList) {
		List<byte[]> keyArr = getKeyList(clz, keyList);//转换成缓存需要的keyList
		
		List<byte[]> bytesList = JedisConnector_Cache.getInstance().mget(keyArr);
		
		if (bytesList == null)
			return new ArrayList<T>();
		
		List<T> objList = new ArrayList<T>();
		for (byte[] bytes : bytesList){
			if (bytes == null)
				continue;
			T t = PersistenceUtil.toObject(clz, bytes);
			if (t == null)
				continue;
			objList.add(t);
		}
		
		return objList;
	}

	/**
	 * FIXME {hash tag}
	 */
	@Override
	public <T> T get(Class<T> clz, String key) {
		key = getSimpleKey(clz,key);
		byte[] bytes = JedisConnector_Cache.getInstance().get(key.getBytes());
		if (bytes == null)
			return null;
		T obj = PersistenceUtil.toObject(clz, bytes);
		return obj;
	}

	@Override
	public void setMapList(Class clz, String key, List<Map<String, Object>> mapList) {
		key = getSimpleKey(clz, key);
		int validSecond =  getValidSecondAdjusted();
		
		JedisConnector_Cache.getInstance().set(key.getBytes(), PersistenceUtil.toBytes(mapList), validSecond);
	}

	@Override
	public List<Map<String, Object>> getMapList(Class clz, String key) {
		
		key = getSimpleKey(clz,key);
		byte[] bytes = JedisConnector_Cache.getInstance().get(key.getBytes());
		if (bytes == null)
			return null;
		List<Map<String, Object>> mapList = PersistenceUtil.toMapList(bytes);
		return mapList;
	}

}
