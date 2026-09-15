package com.remotesupport.backend.dto;

/**
 * The role rides alongside the token so a client can route to a role-appropriate landing page
 * without decoding the JWT itself.
 */
public record LoginResponse(String token, String role) {}
