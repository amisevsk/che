package org.eclipse.che.workspace.infrastructure.kubernetes.timing;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.time.LocalTime;
import org.eclipse.che.api.core.model.workspace.WorkspaceStatus;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.core.notification.EventSubscriber;
import org.eclipse.che.api.workspace.shared.dto.event.MachineStatusEvent;
import org.eclipse.che.api.workspace.shared.dto.event.RuntimeLogEvent;
import org.eclipse.che.api.workspace.shared.dto.event.ServerStatusEvent;
import org.eclipse.che.api.workspace.shared.dto.event.WorkspaceStatusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class TimingLoggerListener {

  private static final Logger LOG = LoggerFactory.getLogger(TimingLoggerListener.class);
  private static final String LOG_FMT = "%s \t %s \t %s";
  private LocalTime lastEvent;
  private LocalTime startTime;

  @Inject
  public TimingLoggerListener(EventService eventService) {
    eventService.subscribe(new MachineStatusEventSubscriber(), MachineStatusEvent.class);
    eventService.subscribe(new RuntimeLogEventSubscriber(), RuntimeLogEvent.class);
    eventService.subscribe(new ServerStatusEventSubscriber(), ServerStatusEvent.class);
    eventService.subscribe(new WorkspaceStatusEventSubscriber(), WorkspaceStatusEvent.class);
    lastEvent = LocalTime.now();
    startTime = LocalTime.now();
  }

  private class WorkspaceStatusEventSubscriber implements EventSubscriber<WorkspaceStatusEvent> {
    @Override
    public void onEvent(WorkspaceStatusEvent event) {
      if (event.getStatus().equals(WorkspaceStatus.STARTING)) {
        lastEvent = LocalTime.now();
        startTime = LocalTime.now();
        logEvent(String.format("Starting workspace %s", event.getWorkspaceId()));
      } else if (event.getStatus().equals(WorkspaceStatus.RUNNING)) {
        logEvent(String.format("Workspace %s start completed", event.getWorkspaceId()));
      }
    }
  }

  private class MachineStatusEventSubscriber implements EventSubscriber<MachineStatusEvent> {
    @Override
    public void onEvent(MachineStatusEvent event) {
      logEvent(
          String.format(
              "%s - %s - %s",
              event.getEventType(), event.getMachineName(), event.getIdentity().getWorkspaceId()));
    }
  }

  private class RuntimeLogEventSubscriber implements EventSubscriber<RuntimeLogEvent> {
    @Override
    public void onEvent(RuntimeLogEvent event) {
      logEvent(String.format("%s - %s", event.getText(), event.getMachineName()));
    }
  }

  private class ServerStatusEventSubscriber implements EventSubscriber<ServerStatusEvent> {
    @Override
    public void onEvent(ServerStatusEvent event) {
      logEvent(
          String.format(
              "%s - %s - %s", event.getServerName(), event.getMachineName(), event.getStatus()));
    }
  }

  private void logEvent(String info) {
    LocalTime eventTime = LocalTime.now();
    LOG.error(
        String.format(
            LOG_FMT,
            Duration.between(startTime, eventTime).toMillis(),
            Duration.between(lastEvent, eventTime).toMillis(),
            info));
    lastEvent = eventTime;
  }
}
