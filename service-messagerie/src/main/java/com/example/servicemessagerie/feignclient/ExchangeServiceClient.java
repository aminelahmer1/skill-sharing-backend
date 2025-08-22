package com.example.servicemessagerie.feignclient;

import com.example.servicemessagerie.dto.CommunityMemberResponse;
import com.example.servicemessagerie.dto.ExchangeResponse;
import com.example.servicemessagerie.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "service-exchange", url = "${application.config.exchange-url}")
public interface ExchangeServiceClient {
    @GetMapping("/skill/{skillId}")
    List<ExchangeResponse> getExchangesBySkillId(@PathVariable Integer skillId, @RequestHeader("Authorization") String token);
    /**
     * Récupère tous les subscribers d'un producteur
     * @param token Token d'autorisation
     * @return Liste des utilisateurs abonnés
     */
    @GetMapping("/producer/subscribers")
    List<UserResponse> getAllSubscribersForProducer(@RequestHeader("Authorization") String token);

    /**
     * Récupère tous les membres de la communauté pour un receiver
     * @param token Token d'autorisation
     * @return Liste des membres de la communauté
     */
    @GetMapping("/receiver/community/members")
    List<CommunityMemberResponse> getAllCommunityMembersForReceiver(@RequestHeader("Authorization") String token);

    /**
     * Récupère les utilisateurs d'une compétence spécifique (version simple)
     * @param skillId ID de la compétence
     * @param token Token d'autorisation
     * @return Liste des utilisateurs de la compétence
     */
    @GetMapping("/skill/{skillId}/users/simple")
    List<UserResponse> getSkillUsersSimple(@PathVariable Integer skillId, @RequestHeader("Authorization") String token);
}
