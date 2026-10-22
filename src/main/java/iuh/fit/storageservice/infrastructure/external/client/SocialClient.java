package iuh.fit.storageservice.infrastructure.external.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "GOATSPORTS-SOCIAL-SERVICE",
        url = "${social.service.url}",
        path = "/internal/conversations",
        configuration = SocialClientConfig.class
)
public interface SocialClient {

    @GetMapping("/attachments/access")
    boolean canViewAttachment(@RequestParam("userId") String userId, @RequestParam("key") String key);
}
