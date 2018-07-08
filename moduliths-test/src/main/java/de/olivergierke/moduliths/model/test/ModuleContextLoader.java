/*
 * Copyright 2018 the original author or authors.
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
package de.olivergierke.moduliths.model.test;

import de.olivergierke.moduliths.model.Module;
import de.olivergierke.moduliths.model.Modules;
import de.olivergierke.moduliths.model.test.ModuleContextLoader.SomeConfig.MyImpo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.domain.EntityScanPackages;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.test.context.ContextLoader;
import org.springframework.test.context.MergedContextConfiguration;

/**
 * Dedicated {@link ContextLoader} implementation to reconfigure both {@link AutoConfigurationPackages} and
 * {@link EntityScanPackages} to both only consider the package of the current test class.
 * 
 * @author Oliver Gierke
 */
@Slf4j
class ModuleContextLoader extends SpringBootContextLoader {

	/* 
	 * (non-Javadoc)
	 * @see org.springframework.boot.test.context.SpringBootContextLoader#getInitializers(org.springframework.test.context.MergedContextConfiguration, org.springframework.boot.SpringApplication)
	 */
	@Override
	protected List<ApplicationContextInitializer<?>> getInitializers(MergedContextConfiguration config,
			SpringApplication application) {

		List<ApplicationContextInitializer<?>> initializers = new ArrayList<>(super.getInitializers(config, application));

		initializers.add(applicationContext -> {

			ModuleTestExecution execution = ModuleTestExecution.of(config.getTestClass());

			logModules(execution);

			List<String> basePackages = execution.getBasePackages().collect(Collectors.toList());
			ConfigurableListableBeanFactory beanFactory = applicationContext.getBeanFactory();

			// beanFactory.registerSingleton("foobar", new BeanPostProcessor() {
			//
			// public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
			//
			// if (!bean.getClass().getName().endsWith("BasePackages")) {
			// return bean;
			// }
			//
			// Field field = ReflectionUtils.findField(bean.getClass(), "packages");
			// ReflectionUtils.makeAccessible(field);
			// ReflectionUtils.setField(field, bean, basePackages);
			//
			// return bean;
			// }
			// });

			beanFactory.registerSingleton("__moduleTestExecution", execution);

			// BeanFactoryPostProcessorImplementation postProcessor = new
			// BeanFactoryPostProcessorImplementation(basePackages);

			// beanFactory.registerSingleton("bla", new MyImpo(basePackages));
			// applicationContext.addBeanFactoryPostProcessor(postProcessor);

			// ((AnnotationConfigApplicationContext) applicationContext).register(SomeConfig.class);
		});

		return initializers;
	}

	@Configuration
	@Order(Ordered.LOWEST_PRECEDENCE)
	@Import(MyImpo.class)
	static class SomeConfig {

		@RequiredArgsConstructor
		static class MyImpo implements ImportBeanDefinitionRegistrar {

			// private final List<String> packageNames;

			/* 
			 * (non-Javadoc)
			 * @see org.springframework.context.annotation.ImportBeanDefinitionRegistrar#registerBeanDefinitions(org.springframework.core.type.AnnotationMetadata, org.springframework.beans.factory.support.BeanDefinitionRegistry)
			 */
			@Override
			public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {

				setPackageOnBeanNamed(registry, AutoConfigurationPackages.class.getName());
				setPackageOnBeanNamed(registry, EntityScanPackages.class.getName());
			}

			private void setPackageOnBeanNamed(BeanDefinitionRegistry beanFactory, String beanName) {

				if (beanFactory.containsBeanDefinition(beanName)) {

					BeanDefinition definition = beanFactory.getBeanDefinition(beanName);
					definition.getConstructorArgumentValues().addIndexedArgumentValue(0, Arrays.asList("foo"));
				}

				else {

					BeanDefinitionBuilder builder = BeanDefinitionBuilder
							.rootBeanDefinition(beanName == AutoConfigurationPackages.class.getName()
									? "org.springframework.boot.autoconfigure.AutoConfigurationPackages.BasePackages"
									: "org.springframework.boot.autoconfigure.domain.EntityScanPackages");
					// builder.addConstructorArgValue(packageNames.toArray(new String[packageNames.size()]));

					beanFactory.registerBeanDefinition(beanName, builder.getBeanDefinition());
				}
			}
		}
	}

	private static void logModules(ModuleTestExecution execution) {

		Module module = execution.getModule();
		Modules modules = execution.getModules();
		String moduleName = module.getDisplayName();
		String bootstrapMode = execution.getBootstrapMode().name();

		String message = String.format("Bootstrapping @ModuleTest for %s in mode %s…", moduleName, bootstrapMode);

		LOG.info(message);
		LOG.info(getSeparator("=", message));

		Arrays.stream(module.toString(modules).split("\n")).forEach(LOG::info);

		List<Module> dependencies = execution.getDependencies();

		if (!dependencies.isEmpty()) {

			LOG.info(getSeparator("=", message));
			LOG.info("Included dependencies:");
			LOG.info(getSeparator("=", message));

			dependencies.stream() //
					.map(it -> it.toString(modules)) //
					.forEach(it -> {
						Arrays.stream(it.split("\n")).forEach(LOG::info);
					});

			LOG.info(getSeparator("=", message));
		}
	}

	private static String getSeparator(String character, String reference) {
		return String.join("", Collections.nCopies(reference.length(), character));
	}

	/**
	 * @author Oliver Gierke
	 */
	@RequiredArgsConstructor
	private static class BeanFactoryPostProcessorImplementation
			implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

		private final List<String> packageNames;

		/* 
		 * (non-Javadoc)
		 * @see org.springframework.core.Ordered#getOrder()
		 */
		@Override
		public int getOrder() {
			return Ordered.LOWEST_PRECEDENCE;
		}

		private void setPackageOnBeanNamed(BeanDefinitionRegistry beanFactory, String beanName) {

			if (beanFactory.containsBeanDefinition(beanName)) {

				BeanDefinition definition = beanFactory.getBeanDefinition(beanName);
				definition.getConstructorArgumentValues().addIndexedArgumentValue(0, packageNames);
			}

			else {

				BeanDefinitionBuilder builder = BeanDefinitionBuilder
						.rootBeanDefinition(beanName == AutoConfigurationPackages.class.getName()
								? "org.springframework.boot.autoconfigure.AutoConfigurationPackages.BasePackages"
								: "org.springframework.boot.autoconfigure.domain.EntityScanPackages");
				builder.addConstructorArgValue(packageNames.toArray(new String[packageNames.size()]));
			}
		}

		/* 
		 * (non-Javadoc)
		 * @see org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor#postProcessBeanDefinitionRegistry(org.springframework.beans.factory.support.BeanDefinitionRegistry)
		 */
		@Override
		public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
			setPackageOnBeanNamed(registry, AutoConfigurationPackages.class.getName());
			setPackageOnBeanNamed(registry, EntityScanPackages.class.getName());

		}

		/* 
		 * (non-Javadoc)
		 * @see org.springframework.beans.factory.config.BeanFactoryPostProcessor#postProcessBeanFactory(org.springframework.beans.factory.config.ConfigurableListableBeanFactory)
		 */
		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

			// setPackageOnBeanNamed(beanFactory, AutoConfigurationPackages.class.getName());
			// setPackageOnBeanNamed(beanFactory, EntityScanPackages.class.getName());
		}
	}
}
