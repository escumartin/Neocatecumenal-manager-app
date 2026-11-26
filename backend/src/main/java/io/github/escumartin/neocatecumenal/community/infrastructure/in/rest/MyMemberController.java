package io.github.escumartin.neocatecumenal.community.infrastructure.in.rest;

import io.github.escumartin.neocatecumenal.identity.TransactionRetry;
import io.github.escumartin.neocatecumenal.community.application.port.in.ChangeOwnContact;
import io.github.escumartin.neocatecumenal.community.application.port.in.GetOwnMember;
import io.github.escumartin.neocatecumenal.community.application.port.in.OwnMemberView;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.generated.api.MyMemberApi;
import io.github.escumartin.neocatecumenal.generated.model.ContactInput;
import io.github.escumartin.neocatecumenal.generated.model.MyMember;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MyMemberController implements MyMemberApi {

    private final ActorResolver actors;
    private final TransactionRetry retry;
    private final GetOwnMember getOwnMember;
    private final ChangeOwnContact changeOwnContact;

    MyMemberController(ActorResolver actors, TransactionRetry retry, GetOwnMember getOwnMember,
            ChangeOwnContact changeOwnContact) {
        this.actors = actors;
        this.retry = retry;
        this.getOwnMember = getOwnMember;
        this.changeOwnContact = changeOwnContact;
    }

    @Override
    public ResponseEntity<MyMember> getMyMember() {
        Actor actor = actors.currentActor();
        return ResponseEntity.ok(MyMemberMapper.toApi(getOwnMember.get(actor), actor));
    }

    @Override
    public ResponseEntity<MyMember> changeMyContact(ContactInput contactInput) {
        Actor actor = actors.currentActor();
        OwnMemberView changed = retry.execute(() -> changeOwnContact.change(actor,
                MembersMapper.toContactChange(contactInput.getMobile(), contactInput.getLandline(),
                        contactInput.getEmail())));
        return ResponseEntity.ok(MyMemberMapper.toApi(changed, actor));
    }
}
