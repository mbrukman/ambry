package com.github.ambry.clustermap;

import com.github.ambry.utils.SystemTime;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * FixedBackoffResourceStatePolicy marks a resource as unavailable for retryBackoff milliseconds if the number of
 * consecutive errors the resource encountered is greater than failureCountThreshold.
 */
class FixedBackoffResourceStatePolicy implements ResourceStatePolicy {
  private final Object resource;
  private final boolean hardDown;
  private final AtomicInteger failureCount;
  private final int failureCountThreshold;
  private final long retryBackoffMs;
  private AtomicLong downUntil;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  public FixedBackoffResourceStatePolicy(Object resource, boolean hardDown,  int failureCountThreshold,
      long retryBackoffMs) {
    this.resource = resource;
    this.hardDown = hardDown;
    this.failureCountThreshold = failureCountThreshold;
    this.retryBackoffMs = retryBackoffMs;
    this.downUntil = new AtomicLong(0);
    this.failureCount = new AtomicInteger(0);
  }

  /*
   * On an error, if the failureCount is greater than the threshold, mark the node as down.
   */
  @Override
  public void onError() {
      if (failureCount.incrementAndGet() >= failureCountThreshold) {
        downUntil.set(SystemTime.getInstance().milliseconds() + retryBackoffMs);
        logger.error("Resource " + resource + " has gone down");
      }
  }

  /*
   * A single response resets the count.
   */
  @Override
  public void onSuccess() {
    failureCount.set(0);
  }

  /*
   * If the number of failures are above the threshold, the resource will be counted as down unless downUntil is in
   * the past.
   * Note how failureCount is not reset to 0 here. This is so that the node is marked as down if the first request after
   * marking a node back up, also times out. We only reset failureCount on an actual response, so down nodes get a
   * 'chance' to prove they are back up every retryBackoffMs - they do not get 'fully up' status until they are actually
   * responsive.
   */
  @Override
  public boolean isDown() {
    boolean down = false;
    if (hardDown) {
      down = true;
    } else if (failureCount.get() >= failureCountThreshold) {
      if (SystemTime.getInstance().milliseconds() < downUntil.get()) {
        down = true;
      }
    }
    return down;
  }

  @Override
  public boolean isHardDown() {
    return hardDown;
  }
}