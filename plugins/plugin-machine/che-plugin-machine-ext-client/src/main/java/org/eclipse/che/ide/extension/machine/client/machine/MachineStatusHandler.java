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
package org.eclipse.che.ide.extension.machine.client.machine;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.api.machine.shared.dto.MachineDto;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.workspace.shared.dto.WorkspaceDto;
import org.eclipse.che.api.workspace.shared.dto.WorkspaceRuntimeDto;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.machine.MachineEntity;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.api.workspace.WorkspaceServiceClient;
import org.eclipse.che.ide.api.workspace.event.MachineStatusChangedEvent;
import org.eclipse.che.ide.extension.machine.client.MachineLocalizationConstant;
import org.eclipse.che.ide.extension.machine.client.inject.factories.EntityFactory;
import org.eclipse.che.ide.ui.loaders.LoaderPresenter;

import static org.eclipse.che.ide.api.notification.StatusNotification.DisplayMode.EMERGE_MODE;
import static org.eclipse.che.ide.api.notification.StatusNotification.Status.FAIL;
import static org.eclipse.che.ide.api.notification.StatusNotification.Status.SUCCESS;
import static org.eclipse.che.ide.extension.machine.client.machine.MachineStateEvent.MachineAction.CREATING;
import static org.eclipse.che.ide.extension.machine.client.machine.MachineStateEvent.MachineAction.RUNNING;

/**
 * Notifies about changing machine state.
 *
 * @author Artem Zatsarynnyi
 * @author Roman Nikitenko
 */
@Singleton
public class MachineStatusHandler implements MachineStatusChangedEvent.Handler {

    private final EventBus                    eventBus;
    private final AppContext                  appContext;
    private final EntityFactory               entityFactory;
    private final WorkspaceServiceClient      workspaceServiceClient;
    private final NotificationManager         notificationManager;
    private final MachineLocalizationConstant locale;
    private final LoaderPresenter             loader;

    @Inject
    MachineStatusHandler(final EventBus eventBus,
                         final AppContext appContext,
                         final EntityFactory entityFactory,
                         final WorkspaceServiceClient workspaceServiceClient,
                         final NotificationManager notificationManager,
                         final MachineLocalizationConstant locale,
                         final LoaderPresenter loader) {
        this.eventBus = eventBus;
        this.appContext = appContext;
        this.entityFactory = entityFactory;
        this.workspaceServiceClient = workspaceServiceClient;
        this.notificationManager = notificationManager;
        this.locale = locale;
        this.loader = loader;

        eventBus.addHandler(MachineStatusChangedEvent.TYPE, this);
    }

    @Override
    public void onMachineStatusChanged(final MachineStatusChangedEvent event) {
        final String machineName = event.getMachineName();
        final String machineId = event.getMachineId();
        final String workspaceId = event.getWorkspaceId();

        workspaceServiceClient.getWorkspace(workspaceId).then(new Operation<WorkspaceDto>() {
            @Override
            public void apply(WorkspaceDto workspace) throws OperationException {
                WorkspaceRuntimeDto workspaceRuntime = workspace.getRuntime();
                if (workspaceRuntime == null) {
                    return;
                }

                appContext.setWorkspace(workspace);

                switch (event.getEventType()) {
                    case CREATING:
                        handleMachineCreating(machineId, workspaceRuntime);
                        break;
                    case RUNNING:
                        handleMachineRunning(machineId, workspaceRuntime);
                        break;
                    case DESTROYED:
                        notificationManager.notify(locale.notificationMachineDestroyed(machineName), SUCCESS, EMERGE_MODE);
                        break;
                    case ERROR:
                        notificationManager.notify(event.getErrorMessage(), FAIL, EMERGE_MODE);
                        break;
                }
            }
        });
    }

    private MachineEntity getMachine(String machineId, WorkspaceRuntimeDto workspaceRuntime) {
        for (MachineDto machineDto : workspaceRuntime.getMachines()) {
            if (machineId.equals(machineDto.getId())) {
                return entityFactory.createMachine(machineDto);
            }
        }
        notificationManager.notify(locale.failedToFindMachine(machineId));
        return null;
    }

    private void handleMachineCreating(final String machineId, final WorkspaceRuntimeDto workspaceRuntime) {
        final MachineEntity machine = getMachine(machineId, workspaceRuntime);
        if (machine == null) {
            return;
        }

        if (machine.isDev()) {
            // Will be used later
            // loader.setProgress(LoaderPresenter.Phase.STARTING_WORKSPACE_RUNTIME, LoaderPresenter.Status.LOADING);
        }
        eventBus.fireEvent(new MachineStateEvent(machine, CREATING));
    }

    private void handleMachineRunning(final String machineId, final WorkspaceRuntimeDto workspaceRuntime) {
        final MachineEntity machine = getMachine(machineId, workspaceRuntime);
        if (machine == null) {
            return;
        }

        if (machine.isDev()) {
            // Will be used later
            // loader.setProgress(LoaderPresenter.Phase.STARTING_WORKSPACE_RUNTIME, LoaderPresenter.Status.SUCCESS);
        }
        notificationManager.notify(locale.notificationMachineIsRunning(machine.getDisplayName()), SUCCESS, EMERGE_MODE);
        eventBus.fireEvent(new MachineStateEvent(machine, RUNNING));
    }
}
