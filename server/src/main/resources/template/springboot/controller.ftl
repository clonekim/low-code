package ${packageName};

import com.koreanair.model.User;
import com.koreanair.security.Credential;
import com.koreanair.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.stream.Collectors;



@RestController
@RequestMapping("/api")
public class DashboardController {

    @Autowired
    UserService userService;


    @GetMapping("profile")
    public ResponseEntity<User> getProfile(@Credential Authentication credential) {
        User user = new User();
        user.setEmpNo(credential.getName());
        return ResponseEntity.ok(
                userService.getUser(user)
        );
    }

    @PutMapping("profile")
    public ResponseEntity<User> updateProfile(@Credential Authentication credential, @RequestBody @Valid User user) {
        user.setEmpNo(credential.getName());
        return ResponseEntity.ok(
                userService.updateUser(user));
    }


    @GetMapping("help-users")
    public ResponseEntity<?> helpUsers() {
        return ResponseEntity.ok(userService.getHelpUsers());
    }


    @GetMapping("app-infos")
    public ResponseEntity<?> appInfos(@Credential Authentication credential) {
        return ResponseEntity.ok(
            userService.getAppInfos(credential.getName())
            .stream().filter(appInfo -> appInfo.getApp() != null)
            .collect(Collectors.toList()));
    }

}