package net.datafaker.annotations;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

import net.datafaker.internal.helper.CopyOnWriteMap;
import net.datafaker.transformations.JavaObjectTransformer;
import net.datafaker.transformations.Schema;

import static java.util.Objects.requireNonNull;

public class FakeResolver<T> {

    private static final JavaObjectTransformer JAVA_OBJECT_TRANSFORMER = new JavaObjectTransformer();
    private static final Map<Class<?>, FakeResolver<?>> CLASS_2_FAKE_RESOLVER = new CopyOnWriteMap<>(IdentityHashMap::new);

    private static final Map<Class<?>, Schema<Object, ?>> DEFAULT_SCHEMA_CACHE = new CopyOnWriteMap<>(IdentityHashMap::new);

    private final Class<T> clazz;

    private FakeResolver(Class<T> clazz) {
        this.clazz = clazz;
    }

    public static <T> FakeResolver<T> of(Class<T> clazz) {
        var fakeFactory = CLASS_2_FAKE_RESOLVER.computeIfAbsent(clazz, k -> new FakeResolver<>(clazz));
        return (FakeResolver<T>) fakeFactory;
    }

    public T generate(Schema<Object, ?> schema) {
        if (schema == null) {
            return generateFromDefaultSchema();
        }

        return (T) JAVA_OBJECT_TRANSFORMER.apply(clazz, schema);
    }

    private T generateFromDefaultSchema() {
        Schema<Object, ?> useSchema = DEFAULT_SCHEMA_CACHE.computeIfAbsent(clazz, (__) -> {
            FakeForSchema fakeForSchemaAnnotation = checkFakeAnnotation(clazz);
            return getSchema(fakeForSchemaAnnotation.value());
        });

        return (T) JAVA_OBJECT_TRANSFORMER.apply(clazz, useSchema);
    }

    private Schema<Object, T> getSchema(String pathToSchema) {
        if (pathToSchema != null) {
            try {
                // indexOf(<String>) is faster than indexOf(<char>) since it has jvm intrinsic
                final int sharpIndex = pathToSchema.indexOf("#");
                final Class<?> classToCall;
                final String methodName;
                if (sharpIndex >= 0) {
                    classToCall = Class.forName(pathToSchema.substring(0, sharpIndex));
                    methodName = pathToSchema.substring(sharpIndex + 1);
                } else {
                    classToCall = this.clazz.getEnclosingClass();
                    methodName = pathToSchema;
                }
                Method myStaticMethod = classToCall.getMethod(methodName);
                myStaticMethod.setAccessible(true);
                return (Schema<Object, T>) myStaticMethod.invoke(null);
            } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new IllegalArgumentException("The path to the schema is empty.");
        }
    }

    private FakeForSchema checkFakeAnnotation(Class<T> clazz) {
        requireNonNull(clazz, "The class is null.");

        FakeForSchema annotation = clazz.getAnnotation(FakeForSchema.class);
        if (annotation == null) {
            throw new RuntimeException("The class %s is not annotated with Fake".formatted(clazz.getSimpleName()));
        }
        return annotation;
    }
}
