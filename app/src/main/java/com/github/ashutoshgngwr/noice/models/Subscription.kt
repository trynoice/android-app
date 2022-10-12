package com.github.ashutoshgngwr.noice.models

import com.github.ashutoshgngwr.noice.data.models.SubscriptionDto
import com.github.ashutoshgngwr.noice.data.models.SubscriptionWithPlanDto
import java.io.Serializable
import java.util.*
import com.trynoice.api.client.models.Subscription as ApiSubscription

data class Subscription(
  val id: Long,
  val plan: SubscriptionPlan,
  val isActive: Boolean,
  val isPaymentPending: Boolean,
  val isAutoRenewing: Boolean,
  val isRefunded: Boolean? = null,
  val startedAt: Date? = null,
  val endedAt: Date? = null,
  val renewsAt: Date? = null,
  val googlePlayPurchaseToken: String? = null,
  val giftCardCode: String? = null,
) : Serializable

fun SubscriptionWithPlanDto.toDomainEntity(): Subscription {
  return Subscription(
    id = subscription.id,
    plan = plan.toDomainEntity(),
    isActive = subscription.isActive,
    isPaymentPending = subscription.isPaymentPending,
    isAutoRenewing = subscription.isAutoRenewing,
    isRefunded = subscription.isRefunded,
    startedAt = subscription.startedAt,
    endedAt = subscription.endedAt,
    renewsAt = subscription.renewsAt,
    googlePlayPurchaseToken = subscription.googlePlayPurchaseToken,
    giftCardCode = subscription.giftCardCode,
  )
}

@JvmName("toDomainEntitySubscriptionWithPlanDto")
fun List<SubscriptionWithPlanDto>.toDomainEntity(): List<Subscription> {
  return map { it.toDomainEntity() }
}

fun ApiSubscription.toDomainEntity(): Subscription {
  return Subscription(
    id = id,
    plan = plan.toDomainEntity(),
    isActive = isActive,
    isPaymentPending = isPaymentPending,
    isAutoRenewing = isAutoRenewing,
    isRefunded = isRefunded,
    startedAt = startedAt,
    endedAt = endedAt,
    renewsAt = renewsAt,
    googlePlayPurchaseToken = googlePlayPurchaseToken,
    giftCardCode = giftCardCode,
  )
}

@JvmName("toDomainEntityApiSubscription")
fun List<ApiSubscription>.toDomainEntity(): List<Subscription> {
  return map { it.toDomainEntity() }
}

fun Subscription.toRoomDto(): SubscriptionWithPlanDto {
  return SubscriptionWithPlanDto(
    subscription = SubscriptionDto(
      id = id,
      planId = plan.id,
      isActive = isActive,
      isPaymentPending = isPaymentPending,
      isAutoRenewing = isAutoRenewing,
      isRefunded = isRefunded,
      startedAt = startedAt,
      endedAt = endedAt,
      renewsAt = renewsAt,
      googlePlayPurchaseToken = googlePlayPurchaseToken,
      giftCardCode = giftCardCode,
    ),
    plan = plan.toRoomDto()
  )
}

fun List<Subscription>.toRoomDto(): List<SubscriptionWithPlanDto> {
  return map { it.toRoomDto() }
}
