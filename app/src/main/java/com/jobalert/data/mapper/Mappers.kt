package com.jobalert.data.mapper

import com.jobalert.data.local.entity.AlertEntity
import com.jobalert.data.local.entity.EmailAccountEntity
import com.jobalert.data.local.entity.RuleEntity
import com.jobalert.domain.model.Alert
import com.jobalert.domain.model.EmailAccount
import com.jobalert.domain.model.Rule

// --- Rule ---

fun RuleEntity.toDomain(): Rule = Rule(
    id = id,
    name = name,
    senders = senders,
    subjectKeywords = subjectKeywords,
    bodyKeywords = bodyKeywords,
    isEnabled = isEnabled,
    alertColor = alertColor,
    position = position
)

fun Rule.toEntity(): RuleEntity = RuleEntity(
    id = id,
    name = name,
    senders = senders,
    subjectKeywords = subjectKeywords,
    bodyKeywords = bodyKeywords,
    isEnabled = isEnabled,
    alertColor = alertColor,
    position = position
)

// --- Alert ---

fun AlertEntity.toDomain(): Alert = Alert(
    id = id,
    timestamp = timestamp,
    sender = sender,
    subject = subject,
    snippet = snippet,
    acknowledged = acknowledged,
    ruleName = ruleName
)

fun Alert.toEntity(): AlertEntity = AlertEntity(
    id = id,
    timestamp = timestamp,
    sender = sender,
    subject = subject,
    snippet = snippet,
    acknowledged = acknowledged,
    ruleName = ruleName
)

// --- EmailAccount ---

fun EmailAccountEntity.toDomain(): EmailAccount = EmailAccount(
    id = id,
    email = email,
    host = host,
    port = port,
    useSsl = useSsl,
    isEnabled = isEnabled,
    lastSeenUid = lastSeenUid,
    uidValidity = uidValidity,
    authType = authType,
    oauthConfig = oauthConfig,
    needsReauth = needsReauth
)

fun EmailAccount.toEntity(): EmailAccountEntity = EmailAccountEntity(
    id = id,
    email = email,
    host = host,
    port = port,
    useSsl = useSsl,
    isEnabled = isEnabled,
    lastSeenUid = lastSeenUid,
    uidValidity = uidValidity,
    authType = authType,
    oauthConfig = oauthConfig,
    needsReauth = needsReauth
)
