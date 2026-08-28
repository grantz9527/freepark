package com.freepark.local.softwareplate;

import java.io.IOException;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.freepark.local.common.api.ApiResponse;
import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.common.i18n.MessageService;
import com.freepark.local.domain.LocalUser;
import com.freepark.local.domain.LocalUserRepository;
import com.freepark.local.domain.UserRole;
import com.freepark.local.yolo26plate.Yolo26PlateClient.RecognitionResult;

@RestController
@RequestMapping("/api/v1/system-settings")
public class SoftwarePlateController {

    private final SoftwarePlateClientRouter router;
    private final LocalUserRepository users;
    private final MessageService messages;

    public SoftwarePlateController(SoftwarePlateClientRouter router, LocalUserRepository users,
            MessageService messages) {
        this.router = router;
        this.users = users;
        this.messages = messages;
    }

    // 通用识别接口：按系统设置选中的 provider 执行；允许用 provider / minConfidence / imageId 临时覆盖
    @PostMapping(value = {"/software-plate/recognize", "/yolo26/recognize"}, consumes = "multipart/form-data")
    public ApiResponse<RecognitionResult> recognize(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "minConfidence", required = false) Double minConfidence,
            @RequestParam(value = "imageId", required = false) String imageId,
            @RequestParam(value = "provider", required = false) SoftwarePlateProvider provider) throws IOException {
        requireAdmin(jwt == null ? null : UUID.fromString(jwt.getSubject()));
        byte[] bytes = image == null ? null : image.getBytes();
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(ErrorCode.SOFTWARE_PLATE_EMPTY_IMAGE);
        }
        RecognitionResult result = router.recognize(bytes, image.getOriginalFilename(), imageId, minConfidence, provider);
        return ApiResponse.ok(messages, result);
    }

    private void requireAdmin(UUID userId) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        LocalUser user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getRole() != UserRole.ADMIN) throw new BusinessException(ErrorCode.FORBIDDEN);
    }
}
