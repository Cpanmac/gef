/*******************************************************************************
 * Copyright (c) 2014, 2016 itemis AG and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Matthias Wienand (itemis AG) - initial API & implementation
 *
 *******************************************************************************/
package org.eclipse.gef.zest.fx.policies;

import org.eclipse.gef.mvc.fx.operations.AbstractCompositeOperation;
import org.eclipse.gef.mvc.fx.operations.ITransactionalOperation;
import org.eclipse.gef.mvc.fx.operations.ReverseUndoCompositeOperation;
import org.eclipse.gef.mvc.fx.policies.AbstractTransactionPolicy;
import org.eclipse.gef.zest.fx.operations.ShowHiddenNeighborsOperation;
import org.eclipse.gef.zest.fx.parts.NodePart;

/**
 * The {@link ShowHiddenNeighborsPolicy} can be installed on {@link NodePart} to
 * show its hidden neighbours
 *
 * @author mwienand
 *
 */
public class ShowHiddenNeighborsPolicy extends AbstractTransactionPolicy {

	@Override
	protected ITransactionalOperation createOperation() {
		return new ReverseUndoCompositeOperation("ShowHiddenNeighbours");
	}

	@Override
	public NodePart getHost() {
		return (NodePart) super.getHost();
	}

	/**
	 * Executes an operation on the history that shows the {@link #getHost()
	 * host} {@link NodePart}.
	 */
	public void showHiddenNeighbors() {
		checkInitialized();
		((AbstractCompositeOperation) getOperation())
				.add(new ShowHiddenNeighborsOperation(getHost().getRoot().getViewer(), getHost()));
		locallyExecuteOperation();
	}

}
