package org.example.authservice.dto;

import org.example.authservice.model.User;
import org.mapstruct.*;

@Mapper(
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public abstract class UserMapper {
    @Mapping(target = "encryptedPassword", source = "password")
    public abstract User map(UserAuthDTO dto);

//    public abstract UserDTO map(User model);
}
