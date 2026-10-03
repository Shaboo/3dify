package com.omni3d.api.security

import com.omni3d.api.repository.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class JwtAuthFilter(
    private val jwtService: JwtService,
    private val userRepository: UserRepository
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val header = request.getHeader("Authorization")

        if (header != null && header.startsWith("Bearer ")) {
            val token = header.substring(7)
            val claims = jwtService.validateToken(token)

            if (claims != null) {
                val userId = UUID.fromString(claims.subject)

                // Resolve admin status from DB
                val userRecord = userRepository.findById(userId)
                val isAdmin = userRecord?.isAdmin ?: false

                val authorities = mutableListOf(SimpleGrantedAuthority("ROLE_USER"))
                if (isAdmin) authorities.add(SimpleGrantedAuthority("ROLE_ADMIN"))

                val auth = UsernamePasswordAuthenticationToken(
                    claims.subject,
                    null,
                    authorities
                )
                SecurityContextHolder.getContext().authentication = auth
            }
        }

        filterChain.doFilter(request, response)
    }
}
