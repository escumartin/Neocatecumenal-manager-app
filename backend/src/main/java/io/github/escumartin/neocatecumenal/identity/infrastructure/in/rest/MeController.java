package io.github.escumartin.neocatecumenal.identity.infrastructure.in.rest;

import io.github.escumartin.neocatecumenal.generated.api.MeApi;
import io.github.escumartin.neocatecumenal.generated.model.Me;
import io.github.escumartin.neocatecumenal.generated.model.PasswordChange;
import io.github.escumartin.neocatecumenal.generated.model.PrivacyNoticeAcceptance;
import io.github.escumartin.neocatecumenal.identity.application.port.in.AcceptPrivacyNotice;
import io.github.escumartin.neocatecumenal.identity.application.port.in.ChangeOwnPassword;
import io.github.escumartin.neocatecumenal.identity.application.port.in.GetMyProfile;
import io.github.escumartin.neocatecumenal.identity.application.port.in.RejectPrivacyNotice;
import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MeController implements MeApi {

    private final GetMyProfile getMyProfile;
    private final AcceptPrivacyNotice acceptPrivacyNotice;
    private final RejectPrivacyNotice rejectPrivacyNotice;
    private final ChangeOwnPassword changeOwnPassword;

    MeController(GetMyProfile getMyProfile, AcceptPrivacyNotice acceptPrivacyNotice,
            RejectPrivacyNotice rejectPrivacyNotice, ChangeOwnPassword changeOwnPassword) {
        this.getMyProfile = getMyProfile;
        this.acceptPrivacyNotice = acceptPrivacyNotice;
        this.rejectPrivacyNotice = rejectPrivacyNotice;
        this.changeOwnPassword = changeOwnPassword;
    }

    @Override
    public ResponseEntity<Me> getMe() {
        return getMyProfile.get()
                .map(MeMapper::toMe)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new InsufficientAuthenticationException("Sesión no válida"));
    }

    @Override
    public ResponseEntity<Void> acceptPrivacyNotice(PrivacyNoticeAcceptance privacyNoticeAcceptance) {
        acceptPrivacyNotice.accept(privacyNoticeAcceptance.getVersion());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> changeMyPassword(PasswordChange passwordChange) {
        changeOwnPassword.change(new PlainPassword(passwordChange.getCurrentPassword()),
                new PlainPassword(passwordChange.getNewPassword()));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> rejectPrivacyNotice() {
        rejectPrivacyNotice.reject();
        return ResponseEntity.noContent().build();
    }
}
