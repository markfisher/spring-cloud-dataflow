/*
 * Copyright 2015-2016 the original author or authors.
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

package org.springframework.cloud.dataflow.server.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.dataflow.artifact.registry.ArtifactRegistration;
import org.springframework.cloud.dataflow.artifact.registry.ArtifactRegistry;
import org.springframework.cloud.dataflow.core.ArtifactCoordinates;
import org.springframework.cloud.dataflow.core.ArtifactType;
import org.springframework.cloud.dataflow.core.ModuleDefinition;
import org.springframework.cloud.dataflow.core.TaskDefinition;
import org.springframework.cloud.dataflow.module.deployer.ModuleDeployer;
import org.springframework.cloud.dataflow.rest.resource.TaskDeploymentResource;
import org.springframework.cloud.dataflow.rest.util.DeploymentPropertiesUtils;
import org.springframework.cloud.dataflow.server.repository.NoSuchTaskDefinitionException;
import org.springframework.cloud.dataflow.server.repository.TaskDefinitionRepository;
import org.springframework.cloud.deployer.resource.maven.MavenProperties;
import org.springframework.cloud.deployer.resource.maven.MavenResource;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for operations for deployment operations.
 *
 * @author Michael Minella
 * @author Marius Bogoevici
 * @author Glenn Renfro
 * @author Mark Fisher
 */
@RestController
@RequestMapping("/tasks/deployments")
@ExposesResourceFor(TaskDeploymentResource.class)
public class TaskDeploymentController {

	private static final String DEFAULT_TASK_DATASOURCE_URL = "jdbc:h2:tcp://localhost:19092/mem:dataflow";

	private static final String DEFAULT_TASK_DATASOURCE_USER_NAME = "sa";

	private static final String DEFAULT_TASK_DATASOURCE_DRIVER_CLASS_NAME = "org.h2.Driver";


	private final TaskDefinitionRepository repository;

	private final TaskLauncher taskLauncher;

	@Value("${spring.datasource.url:#{null}}")
	private String dataSourceUrl;

	@Value("${spring.datasource.username:#{null}}")
	private String dataSourceUserName;

	@Value("${spring.datasource.password:#{null}}")
	private String dataSourcePassword;

	@Value("${spring.datasource.driverClassName:#{null}}")
	private String dataSourceDriverClassName;

	/**
	 * The artifact registry this controller will use to look up modules.
	 */
	private final ArtifactRegistry registry;

	/**
	 * Properties for the resolution of Maven artifacts.
	 */
	private final MavenProperties mavenProperties;

	/**
	 * Creates a {@code TaskDeploymentController} that delegates launching
	 * operations to the provided {@link TaskLauncher}
	 * @param repository the repository this controller will use for task CRUD operations.
	 * @param registry artifact registry this controller will use to look up app coordinates.
	 * @param launcher the launcher this controller will use to launch task apps.
	 * @param mavenProperties  properties for the resolution of Maven artifacts
	 */
	@Autowired
	public TaskDeploymentController(TaskDefinitionRepository repository, ArtifactRegistry registry,
			TaskLauncher taskLauncher, MavenProperties mavenProperties) {
		Assert.notNull(repository, "repository must not be null");
		Assert.notNull(registry, "registry must not be null");
		Assert.notNull(taskLauncher, "TaskLauncher must not be null");
		this.repository = repository;
		this.registry = registry;
		this.taskLauncher = taskLauncher;
		this.mavenProperties = mavenProperties;
	}

	/**
	 * Request the launching of an existing task definition.  The name must be
	 * included in the path.
	 *
	 * @param name the name of the existing task to be executed (required)
	 * @param properties the runtime properties for the task, as a comma-delimited list of
	 * 					 key=value pairs
	 */
	@RequestMapping(value = "/{name}", method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.CREATED)
	public void deploy(@PathVariable("name") String name, @RequestParam(required = false) String properties) {
		TaskDefinition taskDefinition = this.repository.findOne(name);
		if (taskDefinition == null) {
			throw new NoSuchTaskDefinitionException(name);
		}

		ModuleDefinition module = taskDefinition.getModuleDefinition();
		ArtifactRegistration registration = this.registry.find(module.getName(), ArtifactType.task);
		if (registration == null) {
			throw new IllegalArgumentException(String.format(
					"Module %s of type %s not found in registry", module.getName(), ArtifactType.task));
		}
		ArtifactCoordinates coordinates = registration.getCoordinates();

		Map<String, String> deploymentProperties = new HashMap<>();
		module = updateTaskProperties(module, module.getName() );
		deploymentProperties.putAll(DeploymentPropertiesUtils.parse(properties));
		deploymentProperties.put(ModuleDeployer.GROUP_DEPLOYMENT_ID, taskDefinition.getName()
				+ "-" + System.currentTimeMillis());

		AppDefinition definition = new AppDefinition(module.getLabel(), module.getParameters());
		MavenResource resource = MavenResource.parse(coordinates.toString(), mavenProperties);
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, deploymentProperties);
		this.taskLauncher.launch(request);
	}

	private ModuleDefinition updateTaskProperties(ModuleDefinition moduleDefinition, String taskDefinitionName) {
		ModuleDefinition.Builder builder = ModuleDefinition.Builder.from(moduleDefinition);
		builder.setParameter("spring.datasource.url",
				(StringUtils.hasText(dataSourceUrl)) ? dataSourceUrl :
						DEFAULT_TASK_DATASOURCE_URL);

		builder.setParameter("spring.datasource.username",
				(StringUtils.hasText(dataSourceUserName)) ? dataSourceUserName :
						DEFAULT_TASK_DATASOURCE_USER_NAME);

		if(StringUtils.hasText(dataSourcePassword)) {//password may be empty
			builder.setParameter("spring.datasource.password", dataSourcePassword );
		}

		builder.setParameter("spring.datasource.driverClassName",
				(StringUtils.hasText(dataSourceDriverClassName)) ? dataSourceDriverClassName :
						DEFAULT_TASK_DATASOURCE_DRIVER_CLASS_NAME);

		return builder.build();
	}
}
