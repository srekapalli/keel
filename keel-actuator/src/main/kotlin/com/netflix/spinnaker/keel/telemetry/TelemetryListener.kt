package com.netflix.spinnaker.keel.telemetry

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.patterns.PolledMeter
import com.netflix.spinnaker.keel.actuation.ScheduledResourceCheckStarting
import com.netflix.spinnaker.keel.events.ResourceActuationLaunched
import com.netflix.spinnaker.keel.events.ResourceCheckResult
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

@Component
class TelemetryListener(
  private val spectator: Registry,
  private val clock: Clock
) {
  private val lastResourceCheck: AtomicReference<Instant> = PolledMeter
    .using(spectator)
    .withName(RESOURCE_CHECK_DRIFT_GAUGE)
    .monitorValue(AtomicReference(clock.instant())) {
      Duration
        .between(it.get(), clock.instant())
        .toMillis()
        .toDouble()
    }

  @EventListener(ResourceCheckResult::class)
  fun onResourceChecked(event: ResourceCheckResult) {
    spectator.counter(
      RESOURCE_CHECKED_COUNTER_ID,
      listOf(
        BasicTag("resourceName", event.name),
        BasicTag("apiVersion", event.apiVersion.toString()),
        BasicTag("resourceKind", event.kind),
        BasicTag("resourceState", event.state.name),
        BasicTag("resourceApplication", event.application)
      )
    ).safeIncrement()
  }

  @EventListener(ResourceCheckSkipped::class)
  fun onResourceCheckSkipped(event: ResourceCheckSkipped) {
    spectator.counter(
      RESOURCE_CHECK_SKIPPED_COUNTER_ID,
      listOf(
        BasicTag("resourceName", event.name.value),
        BasicTag("apiVersion", event.apiVersion.toString()),
        BasicTag("resourceKind", event.kind)
      )
    ).safeIncrement()
  }

  @EventListener(ArtifactVersionUpdated::class)
  fun onArtifactVersionUpdated(event: ArtifactVersionUpdated) {
    spectator.counter(
      ARTIFACT_UPDATED_COUNTER_ID,
      listOf(
        BasicTag("artifactName", event.name),
        BasicTag("artifactType", event.type.name)
      )
    ).safeIncrement()
  }

  @EventListener(ScheduledResourceCheckStarting::class)
  fun onScheduledCheckStarting(event: ScheduledResourceCheckStarting) {
    lastResourceCheck.set(clock.instant())
  }

  @EventListener(ResourceActuationLaunched::class)
  fun onResourceActuationLaunched(event: ResourceActuationLaunched) {
    spectator.counter(
      RESOURCE_ACTUATION_LAUNCHED_COUNTER_ID,
      listOf(
        BasicTag("resourceName", event.name),
        BasicTag("apiVersion", event.apiVersion.toString()),
        BasicTag("resourceKind", event.kind),
        BasicTag("resourceApplication", event.application)
      )
    ).safeIncrement()
  }

  private fun Counter.safeIncrement() =
    try {
      increment()
    } catch (ex: Exception) {
      log.error("Exception incrementing {} counter: {}", id().name(), ex.message)
    }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  companion object {
    private const val RESOURCE_CHECKED_COUNTER_ID = "keel.resource.checked"
    private const val RESOURCE_CHECK_SKIPPED_COUNTER_ID = "keel.resource.check.skipped"
    private const val RESOURCE_ACTUATION_LAUNCHED_COUNTER_ID = "keel.resource.actuation.launched"
    private const val ARTIFACT_UPDATED_COUNTER_ID = "keel.artifact.updated"
    private const val RESOURCE_CHECK_DRIFT_GAUGE = "keel.resource.check.drift"
  }
}
