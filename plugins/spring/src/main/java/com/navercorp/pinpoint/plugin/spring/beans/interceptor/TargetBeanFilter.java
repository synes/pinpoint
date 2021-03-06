/*
 * Copyright 2014 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.plugin.spring.beans.interceptor;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.navercorp.pinpoint.bootstrap.config.ProfilerConfig;
import com.navercorp.pinpoint.plugin.spring.beans.SpringBeansConfig;

/**
 * 
 * @author Jongho Moon <jongho.moon@navercorp.com>
 *
 */
public class TargetBeanFilter {
    private final List<Pattern> targetNamePatterns;
    private final List<Pattern> targetClassPatterns;
    private final Set<String> targetAnnotationNames;

    private final Cache transformed = new Cache();
    private final Cache rejected = new Cache();

    public static TargetBeanFilter of(ProfilerConfig profilerConfig) {
        SpringBeansConfig config = new SpringBeansConfig(profilerConfig);
        
        List<String> targetNamePatternStrings = split(config.getSpringBeansNamePatterns());
        List<Pattern> beanNamePatterns = compilePattern(targetNamePatternStrings);

        List<String> targetClassPatternStrings = split(config.getSpringBeansClassPatterns());
        List<Pattern> beanClassPatterns = compilePattern(targetClassPatternStrings);

        List<String> targetAnnotationNames = split(config.getSpringBeansAnnotations());

        return new TargetBeanFilter(beanNamePatterns, beanClassPatterns, targetAnnotationNames);
    }

    private static List<Pattern> compilePattern(List<String> patternStrings) {
        if (patternStrings == null || patternStrings.isEmpty()) {
            return null;
        }
        List<Pattern> beanNamePatterns = new ArrayList<Pattern>(patternStrings.size());
        for (String patternString : patternStrings) {
            Pattern pattern = Pattern.compile(patternString);
            beanNamePatterns.add(pattern);
        }
        return beanNamePatterns;
    }

    private TargetBeanFilter(List<Pattern> targetNamePatterns, List<Pattern> targetClassPatterns, List<String> targetAnnotationNames) {
        this.targetNamePatterns = targetNamePatterns;
        this.targetClassPatterns = targetClassPatterns;
        this.targetAnnotationNames = targetAnnotationNames == null ? null : new HashSet<String>(targetAnnotationNames);
    }

    public boolean isTarget(String beanName, Class<?> clazz) {
        if (transformed.contains(clazz)) {
            return false;
        }

        return isTarget(beanName) || isTarget(clazz);
    }

    private boolean isTarget(String beanName) {
        if (targetNamePatterns != null) {
            for (Pattern pattern : targetNamePatterns) {
                if (pattern.matcher(beanName).matches()) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isTarget(Class<?> clazz) {
        if (rejected.contains(clazz)) {
            return false;
        }

        if (targetAnnotationNames != null) {
            for (Annotation a : clazz.getAnnotations()) {
                if (targetAnnotationNames.contains(a.annotationType().getName())) {
                    return true;
                }
            }

            for (Annotation a : clazz.getAnnotations()) {
                for (Annotation ac : a.annotationType().getAnnotations()) {
                    if (targetAnnotationNames.contains(ac.annotationType().getName())) {
                        return true;
                    }
                }
            }
        }

        if (targetClassPatterns != null) {
            String className = clazz.getName();

            for (Pattern pattern : targetClassPatterns) {
                if (pattern.matcher(className).matches()) {
                    return true;
                }
            }
        }

        rejected.put(clazz);
        return false;
    }

    public void addTransformed(Class<?> clazz) {
        transformed.put(clazz);
    }

    private static List<String> split(String values) {
        if (values == null) {
            return Collections.emptyList();
        }

        String[] tokens = values.split(",");
        List<String> result = new ArrayList<String>(tokens.length);

        for (String token : tokens) {
            String trimmed = token.trim();

            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }

        return result;
    }
}
