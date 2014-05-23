package com.lambdaworks.redis.support;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.google.common.reflect.AbstractInvocationHandler;
import com.lambdaworks.redis.RedisConnectionPool;

/**
 * Invocation Handler with transparent pooling. This handler is thread-safe.
 * 
 * @author <a href="mailto:mpaluch@paluch.biz">Mark Paluch</a>
 * @since 15.05.14 21:14
 */
public class TransparentPoolingInvocationHandler<T> extends AbstractInvocationHandler {

    private RedisConnectionPool<T> pool;
    private long lastCheck;
    private long intervalMs;

    private T cachedConnection;
    private Map<Method, Method> methodCache = new ConcurrentHashMap<Method, Method>();

    /**
     * 
     * @param pool
     * @param recheckInterval
     * @param unit
     */
    public TransparentPoolingInvocationHandler(RedisConnectionPool<T> pool, long recheckInterval, TimeUnit unit) {
        this.pool = pool;
        intervalMs = TimeUnit.MILLISECONDS.convert(recheckInterval, unit);
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected Object handleInvocation(Object proxy, Method method, Object[] args) throws Throwable {

        long now = System.currentTimeMillis();

        if (cachedConnection != null) {
            if (lastCheck + intervalMs < now) {
                pool.freeConnection(cachedConnection);
                cachedConnection = null;
            }
        }

        Method targetMethod = getMethod(method);
        try {
            if (cachedConnection == null) {
                cachedConnection = pool.allocateConnection();
                lastCheck = now;
            }
            return targetMethod.invoke(cachedConnection, args);
        } finally {
            if (method.getName().equals("close")) {
                pool.freeConnection(cachedConnection);
                cachedConnection = null;
            }
        }
    }

    /**
     * Lookup the target method using a cache.
     * 
     * @param method source method
     * @return the target method
     * @throws NoSuchMethodException
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Method getMethod(Method method) throws NoSuchMethodException {
        Method targetMethod = methodCache.get(method);
        if (targetMethod == null) {
            targetMethod = pool.getComponentType().getMethod(method.getName(), method.getParameterTypes());
            methodCache.put(method, targetMethod);
        }
        return targetMethod;
    }

    public RedisConnectionPool<T> getPool() {
        return pool;
    }

    public Object getCachedConnection() {
        return cachedConnection;
    }
}
