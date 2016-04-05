/*
 * Copyright 2016 the original author or authors.
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
 */

package org.springframework.cloud.dataflow.server.repository;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import org.junit.Test;
import org.springframework.cloud.dataflow.core.ModuleDefinition;
import org.springframework.cloud.dataflow.core.StreamDefinition;

public class InMemoryAppDeploymentRepositoryTests {

	@Test
	public void testSimpleSaveFind() {
		StreamDefinition streamDefinition1 = new StreamDefinition("myStream1", "time | log");
		ModuleDefinition[] moduleDefinitions1 = streamDefinition1.getModuleDefinitions().toArray(new ModuleDefinition[0]);
		StreamDefinition streamDefinition2 = new StreamDefinition("myStream1", "time | log");
		ModuleDefinition[] moduleDefinitions2 = streamDefinition1.getModuleDefinitions().toArray(new ModuleDefinition[0]);
		AppDeploymentKey appDeploymentKey1 = new AppDeploymentKey(streamDefinition1, moduleDefinitions1[0]);
		AppDeploymentKey appDeploymentKey2 = new AppDeploymentKey(streamDefinition1, moduleDefinitions1[1]);
		AppDeploymentKey appDeploymentKey3 = new AppDeploymentKey(streamDefinition2, moduleDefinitions2[0]);
		AppDeploymentKey appDeploymentKey4 = new AppDeploymentKey(streamDefinition2, moduleDefinitions2[1]);

		AppDeploymentRepository repository = new InMemoryAppDeploymentRepository();
		AppDeploymentKey saved1 = repository.save(appDeploymentKey1, "id1");
		AppDeploymentKey saved2 = repository.save(appDeploymentKey2, "id2");
		assertThat(appDeploymentKey1.equals(saved1), is(true));
		assertThat(appDeploymentKey2.equals(saved2), is(true));

		String findOne1 = repository.findOne(appDeploymentKey1);
		assertThat(findOne1, notNullValue());
		assertThat(findOne1, is("id1"));
		String findOne2 = repository.findOne(appDeploymentKey3);
		assertThat(findOne2, notNullValue());
		assertThat(findOne2, is("id1"));

		String findOne3 = repository.findOne(appDeploymentKey2);
		assertThat(findOne3, notNullValue());
		assertThat(findOne3, is("id2"));
		String findOne4 = repository.findOne(appDeploymentKey4);
		assertThat(findOne4, notNullValue());
		assertThat(findOne4, is("id2"));
	}
}
