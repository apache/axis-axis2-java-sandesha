/*
 * Copyright  1999-2004 The Apache Software Foundation.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.apache.sandesha2.storage.beanmanagers;

import org.apache.sandesha2.storage.StorageManager;
import org.apache.sandesha2.storage.StorageManagerFactory;
import org.apache.sandesha2.storage.beans.RMBean;
import org.apache.sandesha2.storage.beans.StorageMapBean;

/**
 * @author 
 * 
 */
public class StorageMapBeanMgr implements CRUD {
	private StorageManager storageMgr;
	
	public StorageMapBeanMgr(int storageType) {
		storageMgr = StorageManagerFactory.getStorageManager(storageType);
	}

	public boolean create(RMBean object) {
		if (!(object instanceof StorageMapBean)) {
			throw new IllegalArgumentException();
		} 
		return storageMgr.createStorageMapBean((StorageMapBean) object);
	}
	
	public boolean delete(String primaryKey) {
		return storageMgr.deleteStorageMapBean(primaryKey);
	}
		
	public RMBean retrieve(String primaryKey) {
		return storageMgr.retrieveStorageMapBean(primaryKey);
	}
	
	public boolean update(RMBean bean) {
		if (!(bean instanceof StorageMapBean)) {
			throw new IllegalArgumentException();
		}
		return storageMgr.updateStorageMapBean((StorageMapBean) bean);
	}
}