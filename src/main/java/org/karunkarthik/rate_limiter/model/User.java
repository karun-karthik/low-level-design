package org.karunkarthik.rate_limiter.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class User {
    private final String userId;
    private final UserTier tier;
}
