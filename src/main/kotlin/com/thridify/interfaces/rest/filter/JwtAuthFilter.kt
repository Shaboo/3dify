package com.thridify.interfaces.rest.filter

import com.thridify.application.service.identity.authenticate.AuthenticateJwtApplicationService
import com.thridify.application.service.identity.authenticate.AuthenticateJwtQuery
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthFilter(private val authenticate: AuthenticateJwtApplicationService) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")

        if (header != null && header.startsWith("Bearer ")) {
            val token = header.substring(7)
            val claims = authenticate.execute(AuthenticateJwtQuery(token))

            if (claims != null) {
                val authorities = mutableListOf(SimpleGrantedAuthority("ROLE_USER"))
                if (claims.isAdmin) authorities.add(SimpleGrantedAuthority("ROLE_ADMIN"))

                val auth = UsernamePasswordAuthenticationToken(
                    claims.subject,
                    null,
                    authorities,
                )
                SecurityContextHolder.getContext().authentication = auth
            }
        }

        filterChain.doFilter(request, response)
    }
}
