package org.example.authservice.dto;

import lombok.Getter;
import lombok.Setter;
import org.example.authservice.model.Role;

@Getter
@Setter
public class UserDTO {
    private String id;
    private String username;
    private Role role;
}
