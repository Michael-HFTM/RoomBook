package ch.diamondh3art.roombook.common.user;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Mocked authentication: the caller is identified by the X-User-Id header, the role comes from app_user
@Service
@Transactional(readOnly = true)
public class UserService {

    public static final String USER_HEADER = "X-User-Id";

    private final AppUserRepository appUserRepository;

    public UserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    public AppUser requireUser(long userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user " + userId));
    }

    public AppUser requireAdmin(long userId) {
        AppUser user = requireUser(userId);
        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role required");
        }
        return user;
    }
}
