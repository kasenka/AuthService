package org.example.authservice.dto;

import org.example.authservice.model.UserAuth;
import org.mapstruct.*;

@Mapper(
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public abstract class UserMapper {
    @Mapping(target = "encryptedPassword", source = "password")
    public abstract UserAuth map(UserAuthDTO dto);

    public abstract UserDTO map(UserAuth model);
}
