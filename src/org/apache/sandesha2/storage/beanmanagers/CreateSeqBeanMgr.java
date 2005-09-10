/*
 * Copyright 2004,2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package org.apache.sandesha2.storage.beanmanagers;

import java.sql.ResultSet;
import java.util.Collection;

import org.apache.sandesha2.storage.beans.CreateSeqBean;

/**
 * @author Chamikara Jayalath <chamikara@wso2.com>
 * @author Sanka Samaranayake <ssanka@gmail.com>
 */
public interface CreateSeqBeanMgr {
	public boolean insert(CreateSeqBean bean);
	public boolean delete(String msgId);
	public CreateSeqBean retrieve(String msgId);
	public boolean update(CreateSeqBean bean);
	public Collection find(CreateSeqBean bean);
	public ResultSet find(String query);
}
