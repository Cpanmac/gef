/*******************************************************************************
 * Copyright (c) 2014, 2016 itemis AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Alexander Nyßen (itemis AG) - initial API and implementation
 *
 *******************************************************************************/
package org.eclipse.gef.mvc.fx.domain;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.IOperationHistory;
import org.eclipse.core.commands.operations.IOperationHistoryListener;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.commands.operations.OperationHistoryEvent;
import org.eclipse.core.commands.operations.UndoContext;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.gef.common.activate.ActivatableSupport;
import org.eclipse.gef.common.activate.IActivatable;
import org.eclipse.gef.common.adapt.AdaptableSupport;
import org.eclipse.gef.common.adapt.AdapterKey;
import org.eclipse.gef.common.adapt.inject.InjectAdapters;
import org.eclipse.gef.mvc.fx.operations.AbstractCompositeOperation;
import org.eclipse.gef.mvc.fx.operations.ForwardUndoCompositeOperation;
import org.eclipse.gef.mvc.fx.operations.ITransactionalOperation;
import org.eclipse.gef.mvc.fx.operations.ReverseUndoCompositeOperation;
import org.eclipse.gef.mvc.fx.tools.ITool;
import org.eclipse.gef.mvc.fx.viewer.IViewer;

import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyMapProperty;
import javafx.collections.ObservableMap;

/**
 * The {@link HistoricizingDomain} is an {@link IDomain} that uses an
 * {@link IOperationHistory} for executing {@link ITransactionalOperation
 * ITransactionalOperations}.
 *
 * @author anyssen
 */
public class HistoricizingDomain implements IDomain {

	private static final int DEFAULT_UNDO_LIMIT = 128;
	private static final UncaughtExceptionHandler UNCAUGHT_EXCEPTION_HANDLER = new UncaughtExceptionHandler() {
		@Override
		public void uncaughtException(Thread t, Throwable e) {
			e.printStackTrace();
			if (e instanceof RuntimeException) {
				throw (RuntimeException) e;
			} else {
				throw new RuntimeException(e);
			}
		}
	};

	private ActivatableSupport acs = new ActivatableSupport(this);
	private AdaptableSupport<HistoricizingDomain> ads = new AdaptableSupport<>(
			this);

	private IOperationHistory operationHistory;
	private IUndoContext undoContext;

	private AbstractCompositeOperation transaction;
	private Set<ITool> transactionContext = new HashSet<>();
	private IOperationHistoryListener transactionListener = new IOperationHistoryListener() {
		@Override
		public void historyNotification(OperationHistoryEvent event) {
			if (event.getEventType() == OperationHistoryEvent.ABOUT_TO_UNDO) {
				if (!transactionContext.isEmpty() && transaction != null) {
					if (transaction.getOperations().isEmpty()) {
						for (ITool tool : transactionContext) {
							closeExecutionTransaction(tool);
						}
					} else {
						throw new IllegalStateException(
								"Cannot perform UNDO while a currently open execution transaction contains operations.");
					}
				}
			}
		}
	};

	/**
	 * Creates a new {@link HistoricizingDomain} instance.
	 */
	public HistoricizingDomain() {
		// ensure uncaught exception handler is used
		Thread.currentThread()
				.setUncaughtExceptionHandler(UNCAUGHT_EXCEPTION_HANDLER);
	}

	@Override
	public final void activate() {
		acs.activate(this::doActivate);
	}

	@Override
	public final ReadOnlyBooleanProperty activeProperty() {
		return acs.activeProperty();
	}

	@Override
	public ReadOnlyMapProperty<AdapterKey<?>, Object> adaptersProperty() {
		return ads.adaptersProperty();
	}

	/**
	 * Applies the undo context to the given operation. May be overwritten by
	 * clients to filter out operations that should not be undoable in the given
	 * context.
	 *
	 * @param operation
	 *            The {@link ITransactionalOperation} to apply the
	 *            {@link #getUndoContext()} to.
	 */
	protected void applyUndoContext(ITransactionalOperation operation) {
		// if (operation.isContentRelevant()) {
		operation.addContext(getUndoContext());
		// }
	}

	@Override
	public void closeExecutionTransaction(ITool tool) {
		// if (!transactionContext.contains(tool)) {
		// throw new IllegalStateException(
		// "No transaction active for tool " + tool + ".");
		// }
		if (!transactionContext.contains(tool)) {
			return; // TODO: is this legal?
		}

		// remove tool from the transaction context and close the transaction in
		// case the tool was the last one
		if (transactionContext.size() == 1
				&& transactionContext.contains(tool)) {
			// Close transaction by adding it to the operation history in case
			// it has an effect; all its nested operations have already been
			// executed, thus it does not have to be executed again
			if (transaction == null) {
				throw new IllegalStateException(
						"No transaction is currently active, while the transaction context sill contained tool "
								+ tool + ".");
			}
			List<ITransactionalOperation> operations = transaction
					.getOperations();
			if (!operations.isEmpty()) {
				// use the concatenation of the operations' labels as the
				// transaction label
				StringBuffer label = new StringBuffer();
				int operationCount = operations.size();
				for (int i = 0; i < operationCount; i++) {
					label.append(operations.get(i).getLabel());
					if (operations.size() - 1 > i) {
						label.append(", ");
					}
				}
				transaction.setLabel(label.toString());
				// only add undo context if we have a content related change
				applyUndoContext(transaction);
				getOperationHistory().add(transaction);
			}
			transaction = null;
		}
		transactionContext.remove(tool);
	}

	/**
	 * Creates a {@link ForwardUndoCompositeOperation} which is used to store
	 * the operations within an execution transaction. The operation is opened
	 * on the {@link #getOperationHistory() operation history}.
	 *
	 * @return A new {@link ForwardUndoCompositeOperation} which is configured
	 *         to store the operations within an execution transaction.
	 */
	protected AbstractCompositeOperation createExecutionTransaction() {
		ReverseUndoCompositeOperation transaction = new ReverseUndoCompositeOperation(
				Long.toString(System.currentTimeMillis()));
		return transaction;
	}

	@Override
	public final void deactivate() {
		acs.deactivate(this::doDeactivate);
	}

	@Override
	public void dispose() {
		// dispose transaction related objects
		operationHistory.removeOperationHistoryListener(transactionListener);
		transactionListener = null;
		transactionContext.clear();
		transactionContext = null;
		transaction = null;

		// dispose operation history and undo context
		operationHistory.dispose(undoContext, true, true, true);
		operationHistory = null;
		undoContext = null;

		// dispose adaptable and activatable support
		ads.dispose();
		ads = null;
		acs = null;
	}

	/**
	 * Activates this {@link HistoricizingDomain}, which activates its adapters.
	 */
	protected void doActivate() {
		// XXX: We keep a sorted map of adapters so activation
		// is performed in a deterministic order
		new TreeMap<>(ads.getAdapters()).values().forEach((adapter) -> {
			if (adapter instanceof IActivatable) {
				((IActivatable) adapter).activate();
			}
		});
	}

	/**
	 * Deactivates this {@link HistoricizingDomain}, which deactivates its
	 * adapters.
	 */
	protected void doDeactivate() {
		// XXX: We keep a sorted map of adapters so deactivation
		// is performed in a deterministic order
		new TreeMap<>(ads.getAdapters()).values().forEach((adapter) -> {
			if (adapter instanceof IActivatable) {
				((IActivatable) adapter).deactivate();
			}
		});
	}

	/**
	 * {@inheritDoc}
	 *
	 * In case an execution transaction is currently open (see
	 * {@link #openExecutionTransaction(ITool)},
	 * {@link #closeExecutionTransaction(ITool)}) the enclosing transaction will
	 * refer to the {@link IUndoContext} used by this {@link IDomain}) (so that
	 * no specific {@link IUndoContext} is set on the passed in
	 * {@link IUndoableOperation}). If no transaction is currently open, the
	 * {@link IUndoContext} of this {@link IDomain} will be set on the passed in
	 * {@link IUndoableOperation}.
	 */
	@Override
	public void execute(ITransactionalOperation operation,
			IProgressMonitor monitor) throws ExecutionException {
		// reduce composite operations
		if (operation instanceof AbstractCompositeOperation) {
			operation = ((AbstractCompositeOperation) operation).unwrap(true);
		}
		// do not execute NoOps
		if (operation.isNoOp()) {
			return;
		}
		// check if we can execute operation
		if (!operation.canExecute()) {
			throw new IllegalArgumentException("Operation cannot be executed.");
		}
		if (transaction != null) {
			// execute operation locally and add it to the current transaction
			operation.execute(monitor, null);
			transaction.add(operation);
		} else {
			// execute operation directly on operation history
			applyUndoContext(operation);
			getOperationHistory().execute(operation, monitor, null);
		}
	}

	@Override
	public <T> T getAdapter(AdapterKey<T> key) {
		return ads.getAdapter(key);
	}

	@Override
	public <T> T getAdapter(Class<T> classKey) {
		return ads.<T> getAdapter(classKey);
	}

	@Override
	public <T> T getAdapter(TypeToken<T> key) {
		return ads.getAdapter(key);
	}

	@Override
	public <T> AdapterKey<T> getAdapterKey(T adapter) {
		return ads.getAdapterKey(adapter);
	}

	@Override
	public ObservableMap<AdapterKey<?>, Object> getAdapters() {
		return ads.getAdapters();
	}

	@Override
	public <T> Map<AdapterKey<? extends T>, T> getAdapters(
			Class<? super T> classKey) {
		return ads.getAdapters(classKey);
	}

	@Override
	public <T> Map<AdapterKey<? extends T>, T> getAdapters(
			TypeToken<? super T> key) {
		return ads.getAdapters(key);
	}

	/**
	 * Returns the {@link IOperationHistory} used by this
	 * {@link HistoricizingDomain} to execute transactions.
	 *
	 * @return The {@link IOperationHistory}.
	 */
	public IOperationHistory getOperationHistory() {
		return operationHistory;
	}

	@Override
	public Map<AdapterKey<? extends ITool>, ITool> getTools() {
		return ads.getAdapters(ITool.class);
	}

	/**
	 * Returns the {@link UndoContext} that is used by this domain to execute
	 * transactions.
	 *
	 * @return The {@link UndoContext}.
	 */
	public IUndoContext getUndoContext() {
		return undoContext;
	}

	@Override
	public Map<AdapterKey<? extends IViewer>, IViewer> getViewers() {
		return ads.getAdapters(IViewer.class);
	}

	@Override
	public final boolean isActive() {
		return acs.isActive();
	}

	/**
	 * Returns <code>true</code> if an execution transaction is currently open.
	 * Otherwise returns <code>false</code>.
	 *
	 * @return <code>true</code> if an execution transaction is currently open,
	 *         otherwise <code>false</code>.
	 */
	protected boolean isExecutionTransactionOpen() {
		return transaction != null;
	}

	@Override
	public boolean isExecutionTransactionOpen(ITool tool) {
		return transactionContext.contains(tool);
	}

	@Override
	public void openExecutionTransaction(ITool tool) {
		// if (transactionContext.contains(tool)) {
		// throw new IllegalStateException(
		// "A transaction is already active for tool " + tool + ".");
		// }
		// Create a new transaction in case the tool is the first one to open a
		// transaction.
		if (transactionContext.contains(tool)) {
			return; // TODO: is this legal??
		}
		transactionContext.add(tool);
		if (transactionContext.size() == 1
				&& transactionContext.contains(tool)) {
			if (transaction != null) {
				throw new IllegalStateException(
						"A transaction is already active, while this is the first tool within the transaction context.");
			}
			transaction = createExecutionTransaction();
		}
	}

	@Override
	public <T> void setAdapter(T adapter) {
		ads.setAdapter(adapter);
	}

	@Override
	public <T> void setAdapter(T adapter, String role) {
		ads.setAdapter(adapter, role);
	}

	@Override
	public <T> void setAdapter(TypeToken<T> adapterType, T adapter) {
		ads.setAdapter(adapterType, adapter);
	}

	@InjectAdapters
	@Override
	public <T> void setAdapter(TypeToken<T> adapterType, T adapter,
			String role) {
		ads.setAdapter(adapterType, adapter, role);
	}

	/**
	 * Sets the {@link IOperationHistory} that is used by this
	 * {@link HistoricizingDomain} to the given value. Operation history
	 * listeners are un-/registered accordingly.
	 *
	 * @param operationHistory
	 *            The new {@link IOperationHistory} for this domain.
	 */
	@Inject
	public void setOperationHistory(IOperationHistory operationHistory) {
		if (this.operationHistory != null
				&& this.operationHistory != operationHistory) {
			this.operationHistory
					.removeOperationHistoryListener(transactionListener);
		}
		if (this.operationHistory != operationHistory) {
			this.operationHistory = operationHistory;
			if (this.operationHistory != null) {
				this.operationHistory
						.addOperationHistoryListener(transactionListener);
				if (undoContext != null) {
					this.operationHistory.setLimit(undoContext,
							DEFAULT_UNDO_LIMIT);
				}
			}
		}
	}

	/**
	 * Sets the {@link IUndoContext} that is used by this
	 * {@link HistoricizingDomain} to the given value.
	 *
	 * @param undoContext
	 *            The new {@link IUndoContext} for this domain.
	 */
	@Inject
	public void setUndoContext(IUndoContext undoContext) {
		this.undoContext = undoContext;
		if (operationHistory != null && undoContext != null) {
			operationHistory.setLimit(undoContext, DEFAULT_UNDO_LIMIT);
		}
	}

	@Override
	public <T> void unsetAdapter(T adapter) {
		ads.unsetAdapter(adapter);
	}

}
