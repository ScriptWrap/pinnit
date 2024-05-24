package dev.sasikanth.pinnit.notifications

import com.spotify.mobius.Connectable
import com.spotify.mobius.coroutines.MobiusCoroutines
import com.spotify.mobius.functions.Consumer
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dev.sasikanth.pinnit.scheduler.PinnitNotificationScheduler
import dev.sasikanth.pinnit.utils.DispatcherProvider
import dev.sasikanth.pinnit.utils.notification.NotificationUtil
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

class NotificationsScreenEffectHandler @AssistedInject constructor(
  private val notificationRepository: NotificationRepository,
  private val dispatcherProvider: DispatcherProvider,
  private val notificationUtil: NotificationUtil,
  private val pinnitNotificationScheduler: PinnitNotificationScheduler,
  @Assisted private val viewEffectConsumer: Consumer<NotificationScreenViewEffect>
) {

  @AssistedFactory
  interface Factory {
    fun create(viewEffectConsumer: Consumer<NotificationScreenViewEffect>): NotificationsScreenEffectHandler
  }

  fun build(): Connectable<NotificationsScreenEffect, NotificationsScreenEvent> {
    return MobiusCoroutines.effectHandler(dispatcherProvider.main) { effect, eventConsumer ->
      when (effect) {
        LoadNotifications -> loadNotifications(eventConsumer::accept)

        CheckNotificationsVisibility -> checkNotificationsVisibility()

        is ToggleNotificationPinStatus -> toggleNotificationPinStatus(effect)

        is DeleteNotification -> deleteNotification(effect, eventConsumer::accept)

        is UndoDeletedNotification -> undoDeleteNotification(effect, eventConsumer::accept)

        is ShowUndoDeleteNotification -> showUndoDeleteNotification(effect)

        is CancelNotificationSchedule -> cancelNotificationSchedule(effect)

        is RemoveSchedule -> removeSchedule(effect, eventConsumer::accept)

        is ScheduleNotification -> scheduleNotification(effect)

        CheckPermissionToPostNotification -> checkPermissionToPostNotification(eventConsumer::accept)

        RequestNotificationPermission -> requestNotificationPermission()
      }
    }
  }

  private suspend fun scheduleNotification(effect: ScheduleNotification) {
    withContext(dispatcherProvider.default) {
      pinnitNotificationScheduler.scheduleNotification(effect.notification)
    }
  }

  private suspend fun requestNotificationPermission() {
    withContext(dispatcherProvider.main) {
      viewEffectConsumer.accept(RequestNotificationPermissionViewEffect)
    }
  }

  private suspend fun checkPermissionToPostNotification(dispatchEvent: (NotificationsScreenEvent) -> Unit) {
    withContext(dispatcherProvider.default) {
      val canPostNotifications = notificationUtil.hasPermissionToPostNotifications()
      dispatchEvent(HasPermissionToPostNotifications(canPostNotifications))
    }
  }

  private suspend fun loadNotifications(dispatchEvent: (NotificationsScreenEvent) -> Unit) {
    withContext(dispatcherProvider.io) {
      val notificationsFlow = notificationRepository.notifications()
      notificationsFlow.onEach { dispatchEvent(NotificationsLoaded(it)) }.collect()
    }
  }

  private suspend fun toggleNotificationPinStatus(effect: ToggleNotificationPinStatus) {
    withContext(dispatcherProvider.io) {
      val notification = effect.notification
      // We are doing is before to avoid waiting for the I/O
      // actions to be completed and also to avoid changing the
      // `toggleNotificationPinStatus` method to return the
      // notification.
      if (notification.isPinned) {
        // If already pinned, then dismiss the notification
        notificationUtil.dismissNotification(notification)
      } else {
        // If already is not pinned, then show the notification and pin it
        notificationUtil.showNotification(notification)
      }
      notificationRepository.updatePinStatus(notification.uuid, !notification.isPinned)
    }
  }

  private suspend fun deleteNotification(effect: DeleteNotification, dispatchEvent: (NotificationsScreenEvent) -> Unit) {
    withContext(dispatcherProvider.io) {
      val deletedNotification = notificationRepository.deleteNotification(effect.notification)
      dispatchEvent(NotificationDeleted(deletedNotification))
    }
  }

  private suspend fun showUndoDeleteNotification(effect: ShowUndoDeleteNotification) {
    withContext(dispatcherProvider.main) {
      viewEffectConsumer.accept(UndoNotificationDeleteViewEffect(effect.notification.uuid))
    }
  }

  private suspend fun undoDeleteNotification(effect: UndoDeletedNotification, dispatchEvent: (NotificationsScreenEvent) -> Unit) {
    withContext(dispatcherProvider.io) {
      val notification = notificationRepository.notification(effect.notificationUuid)
      notificationRepository.undoNotificationDelete(notification)

      dispatchEvent(RestoredDeletedNotification(notification))
    }
  }

  /**
   * We will check for notifications visibility only at start, so it's fine
   * to get the Flow as list.
   */
  private suspend fun checkNotificationsVisibility() {
    withContext(dispatcherProvider.io) {
      val notifications = notificationRepository.pinnedNotifications()
      notificationUtil.checkNotificationsVisibility(notifications)
    }
  }

  private suspend fun cancelNotificationSchedule(effect: CancelNotificationSchedule) {
    withContext(dispatcherProvider.default) {
      pinnitNotificationScheduler.cancel(effect.notificationId)
    }
  }

  private suspend fun removeSchedule(effect: RemoveSchedule, dispatchEvent: (NotificationsScreenEvent) -> Unit) {
    withContext(dispatcherProvider.io) {
      notificationRepository.removeSchedule(effect.notificationId)
      dispatchEvent(RemovedNotificationSchedule(effect.notificationId))
    }
  }
}
