/*
 * Copyright 2011-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.valkey.springframework.data.valkey.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.RawTargetAccess;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessException;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import org.springframework.lang.Nullable;
import org.springframework.transaction.support.ResourceHolderSupport;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

/**
 * Helper class that provides static methods for obtaining {@link ValkeyConnection} from a
 * {@link ValkeyConnectionFactory}. Includes special support for Spring-managed transactional ValkeyConnections, e.g.
 * managed by {@link org.springframework.transaction.support.AbstractPlatformTransactionManager}..
 * <p>
 * Used internally by Spring's {@link ValkeyTemplate}. Can also be used directly in application code.
 *
 * @author Costin Leau
 * @author Christoph Strobl
 * @author Thomas Darimont
 * @author Mark Paluch
 * @see #getConnection
 * @see #releaseConnection
 * @see org.springframework.transaction.support.TransactionSynchronizationManager
 */
public abstract class ValkeyConnectionUtils {

	private static final Log log = LogFactory.getLog(ValkeyConnectionUtils.class);

	/**
	 * Obtain a {@link ValkeyConnection} from the given {@link ValkeyConnectionFactory} and binds the connection to the
	 * current thread to be used in closure-scope, if none is already bound. Considers ongoing transactions by reusing the
	 * transaction-bound connection and allows reentrant connection retrieval. Does not bind the connection to potentially
	 * ongoing transactions.
	 *
	 * @param factory connection factory
	 * @return a new Valkey connection without transaction support.
	 */
	public static ValkeyConnection bindConnection(ValkeyConnectionFactory factory) {
		return doGetConnection(factory, true, true, false);
	}

	/**
	 * Obtain a {@link ValkeyConnection} from the given {@link ValkeyConnectionFactory} and binds the connection to the
	 * current thread to be used in closure-scope, if none is already bound. Considers ongoing transactions by reusing the
	 * transaction-bound connection and allows reentrant connection retrieval. Binds also the connection to the ongoing
	 * transaction if no connection is already bound if {@code transactionSupport} is enabled.
	 *
	 * @param factory connection factory.
	 * @param transactionSupport whether transaction support is enabled.
	 * @return a new Valkey connection with transaction support if requested.
	 */
	public static ValkeyConnection bindConnection(ValkeyConnectionFactory factory, boolean transactionSupport) {
		return doGetConnection(factory, true, true, transactionSupport);
	}

	/**
	 * Obtain a {@link ValkeyConnection} from the given {@link ValkeyConnectionFactory}. Is aware of existing connections
	 * bound to the current transaction (when using a transaction manager) or the current thread (when binding a
	 * connection to a closure-scope). Does not bind newly created connections to ongoing transactions.
	 *
	 * @param factory connection factory for creating the connection.
	 * @return an active Valkey connection without transaction management.
	 */
	public static ValkeyConnection getConnection(ValkeyConnectionFactory factory) {
		return getConnection(factory, false);
	}

	/**
	 * Obtain a {@link ValkeyConnection} from the given {@link ValkeyConnectionFactory}. Is aware of existing connections
	 * bound to the current transaction (when using a transaction manager) or the current thread (when binding a
	 * connection to a closure-scope).
	 *
	 * @param factory connection factory for creating the connection.
	 * @param transactionSupport whether transaction support is enabled.
	 * @return an active Valkey connection with transaction management if requested.
	 */
	public static ValkeyConnection getConnection(ValkeyConnectionFactory factory, boolean transactionSupport) {
		return doGetConnection(factory, true, false, transactionSupport);
	}

	/**
	 * Actually obtain a {@link ValkeyConnection} from the given {@link ValkeyConnectionFactory}. Is aware of existing
	 * connections bound to the current transaction (when using a transaction manager) or the current thread (when binding
	 * a connection to a closure-scope). Will create a new {@link ValkeyConnection} otherwise, if {@code allowCreate} is
	 * {@literal true}. This method allows for re-entrance as {@link ValkeyConnectionHolder} keeps track of ref-count.
	 *
	 * @param factory connection factory for creating the connection.
	 * @param allowCreate whether a new (unbound) connection should be created when no connection can be found for the
	 *          current thread.
	 * @param bind binds the connection to the thread, in case one was created-
	 * @param transactionSupport whether transaction support is enabled.
	 * @return an active Valkey connection.
	 */
	public static ValkeyConnection doGetConnection(ValkeyConnectionFactory factory, boolean allowCreate, boolean bind,
			boolean transactionSupport) {

		Assert.notNull(factory, "No ValkeyConnectionFactory specified");

		ValkeyConnectionHolder conHolder = (ValkeyConnectionHolder) TransactionSynchronizationManager.getResource(factory);

		if (conHolder != null && (conHolder.hasConnection() || conHolder.isSynchronizedWithTransaction())) {
			conHolder.requested();
			if (!conHolder.hasConnection()) {
				log.debug("Fetching resumed Valkey Connection from ValkeyConnectionFactory");
				conHolder.setConnection(fetchConnection(factory));
			}
			return conHolder.getRequiredConnection();
		}

		// Else we either got no holder or an empty thread-bound holder here.

		if (!allowCreate) {
			throw new IllegalArgumentException("No connection found and allowCreate = false");
		}

		log.debug("Fetching Valkey Connection from ValkeyConnectionFactory");
		ValkeyConnection connection = fetchConnection(factory);

		boolean bindSynchronization = TransactionSynchronizationManager.isActualTransactionActive() && transactionSupport;

		if (bind || bindSynchronization) {

			if (bindSynchronization && isActualNonReadonlyTransactionActive()) {
				connection = createConnectionSplittingProxy(connection, factory);
			}

			try {
				// Use same ValkeyConnection for further Valkey actions within the transaction.
				// Thread-bound object will get removed by synchronization at transaction completion.
				ValkeyConnectionHolder holderToUse = conHolder;

				if (holderToUse == null) {
					holderToUse = new ValkeyConnectionHolder(connection);
				} else {
					holderToUse.setConnection(connection);
				}

				holderToUse.requested();

				// Consider callback-scope connection binding vs. transaction scope binding
				if (bindSynchronization) {
					potentiallyRegisterTransactionSynchronisation(holderToUse, factory);
				}

				if (holderToUse != conHolder) {
					TransactionSynchronizationManager.bindResource(factory, holderToUse);
				}
			} catch (RuntimeException ex) {
				// Unexpected exception from external delegation call -> close Connection and rethrow.
				releaseConnection(connection, factory);
				throw ex;
			}

			return connection;
		}

		return connection;
	}

	/**
	 * Actually create a {@link ValkeyConnection} from the given {@link ValkeyConnectionFactory}.
	 *
	 * @param factory the {@link ValkeyConnectionFactory} to obtain ValkeyConnections from.
	 * @return a Valkey Connection from the given {@link ValkeyConnectionFactory} (never {@literal null}).
	 * @see ValkeyConnectionFactory#getConnection()
	 */
	private static ValkeyConnection fetchConnection(ValkeyConnectionFactory factory) {
		return factory.getConnection();
	}

	private static void potentiallyRegisterTransactionSynchronisation(ValkeyConnectionHolder connectionHolder,
			final ValkeyConnectionFactory factory) {

		// Should go actually into ValkeyTransactionManager

		if (!connectionHolder.isTransactionActive()) {

			connectionHolder.setTransactionActive(true);
			connectionHolder.setSynchronizedWithTransaction(true);
			connectionHolder.requested();

			ValkeyConnection connection = connectionHolder.getRequiredConnection();
			boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();

			if (!readOnly) {
				connection.multi();
			}

			TransactionSynchronizationManager
					.registerSynchronization(new ValkeyTransactionSynchronizer(connectionHolder, connection, factory, readOnly));
		}
	}

	private static boolean isActualNonReadonlyTransactionActive() {
		return TransactionSynchronizationManager.isActualTransactionActive()
				&& !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
	}

	private static ValkeyConnection createConnectionSplittingProxy(ValkeyConnection connection,
			ValkeyConnectionFactory factory) {

		ProxyFactory proxyFactory = new ProxyFactory(connection);

		proxyFactory.addAdvice(new ConnectionSplittingInterceptor(factory));
		proxyFactory.addInterface(ValkeyConnectionProxy.class);

		return ValkeyConnection.class.cast(proxyFactory.getProxy());
	}

	/**
	 * Closes the given {@link ValkeyConnection}, created via the given factory if not managed externally (i.e. not bound
	 * to the transaction).
	 *
	 * @param conn the Valkey connection to close.
	 * @param factory the Valkey factory that the connection was created with.
	 */
	public static void releaseConnection(@Nullable ValkeyConnection conn, ValkeyConnectionFactory factory) {
		if (conn == null) {
			return;
		}

		ValkeyConnectionHolder conHolder = (ValkeyConnectionHolder) TransactionSynchronizationManager.getResource(factory);
		if (conHolder != null) {

			if (conHolder.isTransactionActive()) {
				if (connectionEquals(conHolder, conn)) {
					if (log.isDebugEnabled()) {
						log.debug("ValkeyConnection will be closed when transaction finished");
					}

					// It's the transactional Connection: Don't close it.
					conHolder.released();
				}
				return;
			}

			// release transactional/read-only and non-transactional/non-bound connections.
			// transactional connections for read-only transactions get no synchronizer registered
			unbindConnection(factory);

			return;
		}

		doCloseConnection(conn);
	}

	/**
	 * Determine whether the given two ValkeyConnections are equal, asking the target {@link ValkeyConnection} in case of a
	 * proxy. Used to detect equality even if the user passed in a raw target Connection while the held one is a proxy.
	 *
	 * @param connectionHolder the {@link ValkeyConnectionHolder} for the held Connection (potentially a proxy)
	 * @param passedInConnetion the {@link ValkeyConnection} passed-in by the user (potentially a target Connection without
	 *          proxy)
	 * @return whether the given Connections are equal
	 * @see #getTargetConnection
	 */
	private static boolean connectionEquals(ValkeyConnectionHolder connectionHolder, ValkeyConnection passedInConnetion) {

		if (!connectionHolder.hasConnection()) {
			return false;
		}

		ValkeyConnection heldConnection = connectionHolder.getRequiredConnection();

		return heldConnection.equals(passedInConnetion) || getTargetConnection(heldConnection).equals(passedInConnetion);
	}

	/**
	 * Return the innermost target {@link ValkeyConnection} of the given {@link ValkeyConnection}. If the given
	 * {@link ValkeyConnection} is a proxy, it will be unwrapped until a non-proxy {@link ValkeyConnection} is found.
	 * Otherwise, the passed-in {@link ValkeyConnection} will be returned as-is.
	 *
	 * @param connection the {@link ValkeyConnection} proxy to unwrap
	 * @return the innermost target Connection, or the passed-in one if no proxy
	 * @see ValkeyConnectionProxy#getTargetConnection()
	 */
	private static ValkeyConnection getTargetConnection(ValkeyConnection connection) {

		ValkeyConnection connectionToUse = connection;

		while (connectionToUse instanceof ValkeyConnectionProxy proxy) {
			connectionToUse = proxy.getTargetConnection();
		}

		return connectionToUse;
	}

	/**
	 * Unbinds and closes the connection (if any) associated with the given factory from closure-scope. Considers ongoing
	 * transactions so transaction-bound connections aren't closed and reentrant closure-scope bound connections. Only the
	 * outer-most call to leads to releasing and closing the connection.
	 *
	 * @param factory Valkey factory
	 */
	public static void unbindConnection(ValkeyConnectionFactory factory) {

		ValkeyConnectionHolder connectionHolder = (ValkeyConnectionHolder) TransactionSynchronizationManager
				.getResource(factory);

		if (connectionHolder == null) {
			return;
		}

		if (log.isDebugEnabled()) {
			log.debug("Unbinding Valkey Connection");
		}

		if (connectionHolder.isTransactionActive()) {
			if (log.isDebugEnabled()) {
				log.debug("Valkey Connection will be closed when outer transaction finished");
			}
		} else {

			ValkeyConnection connection = connectionHolder.getConnection();

			connectionHolder.released();

			if (!connectionHolder.isOpen()) {

				TransactionSynchronizationManager.unbindResourceIfPossible(factory);

				doCloseConnection(connection);
			}
		}
	}

	/**
	 * Return whether the given Valkey connection is transactional, that is, bound to the current thread by Spring's
	 * transaction facilities.
	 *
	 * @param connection Valkey connection to check
	 * @param connectionFactory Valkey connection factory that the connection was created with
	 * @return whether the connection is transactional or not
	 */
	public static boolean isConnectionTransactional(ValkeyConnection connection,
			ValkeyConnectionFactory connectionFactory) {

		Assert.notNull(connectionFactory, "No ValkeyConnectionFactory specified");

		ValkeyConnectionHolder connectionHolder = (ValkeyConnectionHolder) TransactionSynchronizationManager
				.getResource(connectionFactory);

		return connectionHolder != null && connectionEquals(connectionHolder, connection);
	}

	private static void doCloseConnection(@Nullable ValkeyConnection connection) {

		if (connection == null) {
			return;
		}

		if (log.isDebugEnabled()) {
			log.debug("Closing Valkey Connection");
		}

		try {
			connection.close();
		} catch (DataAccessException ex) {
			log.debug("Could not close Valkey Connection", ex);
		} catch (Throwable ex) {
			log.debug("Unexpected exception on closing Valkey Connection", ex);
		}
	}

	/**
	 * A {@link TransactionSynchronization} that makes sure that the associated {@link ValkeyConnection} is released after
	 * the transaction completes.
	 *
	 * @author Christoph Strobl
	 * @author Thomas Darimont
	 * @author Mark Paluch
	 */
	private static class ValkeyTransactionSynchronizer implements TransactionSynchronization {

		private final ValkeyConnectionHolder connectionHolder;
		private final ValkeyConnection connection;
		private final ValkeyConnectionFactory factory;

		private final boolean readOnly;

		ValkeyTransactionSynchronizer(ValkeyConnectionHolder connectionHolder, ValkeyConnection connection,
				ValkeyConnectionFactory factory, boolean readOnly) {

			this.connectionHolder = connectionHolder;
			this.connection = connection;
			this.factory = factory;
			this.readOnly = readOnly;
		}

		@Override
		public void afterCompletion(int status) {

			try {
				if (!readOnly) {
					switch (status) {
						case TransactionSynchronization.STATUS_COMMITTED -> connection.exec();
						case TransactionSynchronization.STATUS_ROLLED_BACK, TransactionSynchronization.STATUS_UNKNOWN ->
							connection.discard();
					}
				}
			} finally {

				if (log.isDebugEnabled()) {
					log.debug("Closing bound connection after transaction completed with " + status);
				}

				connectionHolder.setTransactionActive(false);
				doCloseConnection(connection);
				TransactionSynchronizationManager.unbindResource(factory);
				connectionHolder.reset();
			}
		}
	}

	/**
	 * {@link MethodInterceptor} that invokes read-only commands on a new {@link ValkeyConnection} while read-write
	 * commands are queued on the bound connection.
	 *
	 * @author Christoph Strobl
	 * @author Mark Paluch
	 * @since 1.3
	 */
	static class ConnectionSplittingInterceptor implements MethodInterceptor {

		private final ValkeyConnectionFactory factory;
		private final @Nullable Method commandInterfaceMethod;

		public ConnectionSplittingInterceptor(ValkeyConnectionFactory factory) {
			this.factory = factory;
			this.commandInterfaceMethod = null;
		}

		private ConnectionSplittingInterceptor(ValkeyConnectionFactory factory, Method commandInterfaceMethod) {
			this.factory = factory;
			this.commandInterfaceMethod = commandInterfaceMethod;
		}

		@Override
		public Object invoke(MethodInvocation invocation) throws Throwable {
			return intercept(invocation.getThis(), invocation.getMethod(), invocation.getArguments());
		}

		public Object intercept(Object obj, Method method, Object[] args) throws Throwable {

			if (method.getName().equals("getTargetConnection")) {
				// Handle getTargetConnection method: return underlying ValkeyConnection.
				return obj;
			}

			Class<?> returnType = method.getReturnType();
			String returnTypeName = returnType.getSimpleName();

			// bridge keyCommands etc. to defer target invocations
			if (returnType.isInterface() && returnType.getPackageName().equals("io.valkey.springframework.data.valkey.connection")
					&& returnTypeName.startsWith("Valkey") && returnTypeName.endsWith("Commands")) {

				ProxyFactory proxyFactory = new ProxyFactory(ReflectionUtils.invokeMethod(method, obj));

				proxyFactory.addAdvice(new ConnectionSplittingInterceptor(factory, method));
				proxyFactory.addInterface(ValkeyConnectionProxy.class);
				proxyFactory.addInterface(returnType);

				return proxyFactory.getProxy();
			}

			ValkeyCommand commandToExecute = ValkeyCommand.failsafeCommandLookup(method.getName());

			if (isPotentiallyThreadBoundCommand(commandToExecute)) {

				if (log.isDebugEnabled()) {
					log.debug("Invoke '%s' on bound connection".formatted(method.getName()));
				}

				return invoke(method, obj, args);
			}

			if (log.isDebugEnabled()) {
				log.debug("Invoke '%s' on unbound connection".formatted(method.getName()));
			}

			ValkeyConnection connection = factory.getConnection();
			Object target = connection;
			try {

				if (commandInterfaceMethod != null) {
					target = ReflectionUtils.invokeMethod(commandInterfaceMethod,
							connection);
				}

				return invoke(method, target, args);
			} finally {
				// properly close the unbound connection after executing command
				if (!connection.isClosed()) {
					doCloseConnection(connection);
				}
			}
		}

		private Object invoke(Method method, Object target, Object[] args) throws Throwable {

			try {
				return method.invoke(target, args);
			} catch (InvocationTargetException ex) {
				throw ex.getCause();
			}
		}

		private boolean isPotentiallyThreadBoundCommand(ValkeyCommand command) {
			return ValkeyCommand.UNKNOWN.equals(command) || !command.isReadonly();
		}
	}

	/**
	 * Resource holder wrapping a {@link ValkeyConnection}. {@link ValkeyConnectionUtils} binds instances of this class to
	 * the thread, for a specific {@link ValkeyConnectionFactory}.
	 *
	 * @author Christoph Strobl
	 * @author Mark Paluch
	 */
	private static class ValkeyConnectionHolder extends ResourceHolderSupport {

		@Nullable private ValkeyConnection connection;

		private boolean transactionActive = false;

		/**
		 * Create a new ValkeyConnectionHolder for the given Valkey Connection assuming that there is no ongoing transaction.
		 *
		 * @param connection the Valkey Connection to hold.
		 * @see #ValkeyConnectionHolder(ValkeyConnection, boolean)
		 */
		public ValkeyConnectionHolder(ValkeyConnection connection) {
			this.connection = connection;
		}

		/**
		 * Return whether this holder currently has a {@link ValkeyConnection}.
		 */
		protected boolean hasConnection() {
			return this.connection != null;
		}

		@Nullable
		public ValkeyConnection getConnection() {
			return this.connection;
		}

		public ValkeyConnection getRequiredConnection() {

			ValkeyConnection connection = getConnection();

			if (connection == null) {
				throw new IllegalStateException("No active ValkeyConnection");
			}

			return connection;
		}

		/**
		 * Override the existing {@link ValkeyConnection} handle with the given {@link ValkeyConnection}. Reset the handle if
		 * given {@literal null}.
		 * <p>
		 * Used for releasing the Connection on suspend (with a {@literal null} argument) and setting a fresh Connection on
		 * resume.
		 */
		protected void setConnection(@Nullable ValkeyConnection connection) {
			this.connection = connection;
		}

		/**
		 * Set whether this holder represents an active, managed transaction.
		 *
		 * @see org.springframework.transaction.PlatformTransactionManager
		 */
		protected void setTransactionActive(boolean transactionActive) {
			this.transactionActive = transactionActive;
		}

		/**
		 * Return whether this holder represents an active, managed transaction.
		 */
		protected boolean isTransactionActive() {
			return this.transactionActive;
		}

		/**
		 * Releases the current Connection held by this ConnectionHolder.
		 * <p>
		 * This is necessary for ConnectionHandles that expect "Connection borrowing", where each returned Connection is
		 * only temporarily leased and needs to be returned once the data operation is done, to make the Connection
		 * available for other operations within the same transaction.
		 */
		@Override
		public void released() {
			super.released();
			if (!isOpen()) {
				setConnection(null);
			}
		}

		@Override
		public void clear() {
			super.clear();
			this.transactionActive = false;
		}
	}

	/**
	 * Subinterface of {@link ValkeyConnection} to be implemented by {@link ValkeyConnection} proxies. Allows access to the
	 * underlying target {@link ValkeyConnection}.
	 *
	 * @see ValkeyConnectionUtils#getTargetConnection(ValkeyConnection)
	 * @since 2.4.2
	 */
	public interface ValkeyConnectionProxy extends ValkeyConnection, RawTargetAccess {

		/**
		 * Return the target {@link ValkeyConnection} of this proxy.
		 * <p>
		 * This will typically be the native driver {@link ValkeyConnection} or a wrapper from a connection pool.
		 *
		 * @return the underlying {@link ValkeyConnection} (never {@literal null}).
		 */
		ValkeyConnection getTargetConnection();

	}
}
