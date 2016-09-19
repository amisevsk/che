/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.api.workspace.server;

import org.eclipse.che.api.core.model.workspace.Workspace;

/**
 * This component manages workspace storage.
 * // * This component removes workspace file resources from workspace storage. It's usually used after "delete
 * // * workspace" operation (see more {@link WorkspaceService#delete}).
 * // * WorkspaceProjectStorageCleaner
 *
 * @author Alexander Andrienko
 */
public interface WorkspaceProjectStorageManager {

    /**
     * This method removes all file resources from workspace project storage by {@code workspaceId}.
     * All user's project data will be deleted.
     *
     * @param workspace worspace to remove project storage
     */
    void remove(Workspace workspace);
}
