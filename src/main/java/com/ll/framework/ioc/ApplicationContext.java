package com.ll.framework.ioc;

import com.ll.framework.ioc.annotations.Bean;
import com.ll.framework.ioc.annotations.Component;
import com.ll.framework.ioc.annotations.Configuration;
import com.ll.standard.util.Ut;
import lombok.RequiredArgsConstructor;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

public class ApplicationContext {
    private Reflections reflections;
    private Set<Class<?>> components;
    private Map<String, Object> beans;

    public ApplicationContext(String basePackage) {
        this.reflections = new Reflections(basePackage, Scanners.TypesAnnotated);
        this.components = new HashSet<>();
        this.beans = new HashMap<>();
    }

    public void init() {
        this.components = reflections.getTypesAnnotatedWith(Component.class);

        for (Class<?> component : components) {
            if (component.isInterface()) continue;

            if (component.isAnnotationPresent(Configuration.class)) {
                genBeansFromMethods(component);
                continue;
            }
            genBean(component);
        }
    }

    public void genBeansFromMethods(Class<?> clazz) {
        try {
            Method[] methods = clazz.getDeclaredMethods();
            Object configInstance = clazz.getDeclaredConstructor().newInstance();

            for (Method method : methods) {
                if (method.isAnnotationPresent(Bean.class)) {
                    String beanName = Ut.str.lcfirst(method.getName());

                    List<Object> dependencies = new ArrayList<>();
                    for (Class<?> parameterType : method.getParameterTypes()) {
                        dependencies.add(genBean(parameterType));
                    }

                    Object bean = method.invoke(configInstance, dependencies.toArray());

                    beans.put(beanName, bean);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("'%s' 클래스의 @Bean 메서드 처리 실패".formatted(clazz.getSimpleName()), e);
        }
    }

    public <T> T genBean(Class<?> clazz) {
        String beanName = Ut.str.lcfirst(clazz.getSimpleName());
        if (beans.containsKey(beanName)) {
            return (T) beans.get(beanName);
        }

        try {
            Constructor<?> constructor = Arrays.stream(clazz.getDeclaredConstructors())
                    .filter(c -> c.isAnnotationPresent(RequiredArgsConstructor.class))
                    .findFirst()
                    .orElseGet(() -> clazz.getDeclaredConstructors()[0]);

            List<Object> dependencies = new ArrayList<>();
            for (Class<?> parameterType : constructor.getParameterTypes()) {
                dependencies.add(genBean(parameterType));
            }

            constructor.setAccessible(true);
            Object bean = constructor.newInstance(dependencies.toArray());

            beans.put(beanName, bean);

            return (T) bean;
        } catch (Exception e) {
            throw new RuntimeException("'%s' 빈 생성 실패".formatted(beanName), e);
        }
    }

    public <T> T genBean(String beanName) {
        if (beans.containsKey(beanName)) {
            return (T) beans.get(beanName);
        }

        Class<?> component = components.stream()
                .filter(c -> beanName.equals(Ut.str.lcfirst(c.getSimpleName())))
                .findFirst()
                .orElse(null);

        if (component == null) {
            throw new RuntimeException("'%s' 빈 찾기 실패".formatted(beanName));
        }

        return genBean(component);
    }
}