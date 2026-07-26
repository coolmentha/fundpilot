package com.fundpilot.backend.identityaccess.adapter.web.authentication;

import com.fundpilot.backend.identityaccess.application.query.currentactor.ActorRole;

record RequestIdentity(long userId, ActorRole role) {
}
