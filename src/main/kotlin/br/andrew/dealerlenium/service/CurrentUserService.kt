package br.andrew.dealerlenium.service

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class CurrentUserService {
    fun getCurrentUsernameOrNull(): String? {
        return SecurityContextHolder.getContext().authentication
            ?.name
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}
