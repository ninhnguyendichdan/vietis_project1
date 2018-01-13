package com.mulodo.fiveneed;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpSessionListener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;

import com.mulodo.fiveneed.config.SessionListener;

import springfox.documentation.builders.ParameterBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.schema.ModelRef;
import springfox.documentation.service.Parameter;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

/**
 * Main
 */
@SpringBootApplication(scanBasePackages = { "com.mulodo.fiveneed" }, exclude = { SecurityAutoConfiguration.class })
@EnableSwagger2
public class Main {

	public static void main(String[] args) {
		SpringApplication.run(Main.class, args);
	}

	@Bean
	public Docket swaggerSettings() {
		ParameterBuilder aParameterBuilder = new ParameterBuilder();
		aParameterBuilder.name("access_token").modelRef(new ModelRef("string")).parameterType("header").required(false)
				.build();
		List<Parameter> aParameters = new ArrayList<Parameter>();
		aParameters.add(aParameterBuilder.build());
		return new Docket(DocumentationType.SWAGGER_2).select().apis(RequestHandlerSelectors.any())
				.paths(PathSelectors.any()).build().pathMapping("/").globalOperationParameters(aParameters);
	}

	@Bean
	public HttpSessionListener httpSessionListener() {
		return new SessionListener();
	}
}
