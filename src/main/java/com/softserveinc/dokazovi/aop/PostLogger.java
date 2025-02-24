package com.softserveinc.dokazovi.aop;

import com.softserveinc.dokazovi.dto.post.PostSaveFromUserDTO;
import com.softserveinc.dokazovi.entity.LogEntity;
import com.softserveinc.dokazovi.entity.PostEntity;
import com.softserveinc.dokazovi.entity.UserEntity;
import com.softserveinc.dokazovi.entity.enumerations.PostStatus;
import com.softserveinc.dokazovi.repositories.LogRepository;
import com.softserveinc.dokazovi.repositories.PostRepository;
import com.softserveinc.dokazovi.repositories.UserRepository;
import com.softserveinc.dokazovi.security.UserPrincipal;
import com.softserveinc.dokazovi.service.impl.PostServiceImpl;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.NoSuchElementException;

@Component
@Aspect
@RequiredArgsConstructor
public class PostLogger {

    private final LogRepository logRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;

    @AfterReturning("execution(* com.softserveinc.dokazovi.service.impl.PostServiceImpl.saveFromUser("
            + "com.softserveinc.dokazovi.dto.post.PostSaveFromUserDTO,"
            + "com.softserveinc.dokazovi.security.UserPrincipal))")
    public void saveNewPost(JoinPoint joinPoint) {
        Object[] arguments = joinPoint.getArgs();
        PostSaveFromUserDTO postSaveFromUserDTO = getArgumentFromArrayByClassType(arguments, PostSaveFromUserDTO.class);
        UserPrincipal userPrincipal = getArgumentFromArrayByClassType(arguments, UserPrincipal.class);
        makeEntryInLogs(postSaveFromUserDTO.getTitle(), userPrincipal, "Створено матеріал",
                postSaveFromUserDTO.getId());
    }

    @Around("execution(* com.softserveinc.dokazovi.service.impl.PostServiceImpl.updatePostById("
            + "com.softserveinc.dokazovi.security.UserPrincipal,"
            + "com.softserveinc.dokazovi.dto.post.PostSaveFromUserDTO))")
    public Boolean updatePost(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        Object[] arguments = proceedingJoinPoint.getArgs();
        Integer postId = getPostIdFromObjectInArray(arguments);
        String postEntityBeforeExecutingStatus = postRepository.getOne(postId).getStatus().name();
        final Boolean joinPoint = (Boolean) proceedingJoinPoint.proceed();
        UserPrincipal userPrincipal = getArgumentFromArrayByClassType(arguments, UserPrincipal.class);
        PostSaveFromUserDTO postSaveFromUserDTO = getArgumentFromArrayByClassType(arguments, PostSaveFromUserDTO.class);
        String postEntityChangedStatus = PostStatus.values()[postSaveFromUserDTO.getPostStatus()].name();
        String changes;
        if (postEntityBeforeExecutingStatus.equals(postEntityChangedStatus)) {
            changes = "Оновлено матеріал";
        } else {
            switch (postEntityChangedStatus) {
                case "ARCHIVED":
                    changes = "Заархівовано";
                    break;
                case "MODERATION_FIRST_SIGN":
                    changes = "Відправлено на модерацію";
                    break;
                case "NEEDS_EDITING":
                    changes = "Повернуто автору на редагування";
                    break;
                case "PLANNED":
                    changes = "Заплановано публікацію";
                    break;
                case "PUBLISHED":
                    changes = "Опубліковано";
                    break;
                default:
                    changes = "N/A";
            }
        }
        makeEntryInLogs(postSaveFromUserDTO.getTitle(), userPrincipal, changes, postSaveFromUserDTO.getId());
        return joinPoint;
    }

    @AfterReturning(
            pointcut = "execution(* com.softserveinc.dokazovi.service.impl.PostServiceImpl.removePostById(..))",
            returning = "returnVal"
    )
    public void deletePost(JoinPoint joinPoint, Object returnVal) {
        PostServiceImpl service = (PostServiceImpl) joinPoint.getTarget();
        PostEntity postEntity = service.getPostEntityFromThreadLocal();
        if (Boolean.TRUE.equals(returnVal) && postEntity != null) {
            UserPrincipal userPrincipal = getArgumentFromArrayByClassType(joinPoint.getArgs(), UserPrincipal.class);
            makeEntryInLogs(postEntity.getTitle(), userPrincipal, "Матеріал видалено", postEntity.getId());
            service.clearPostEntityThreadLocal();
        }
    }

    private void makeEntryInLogs(String title, UserPrincipal userPrincipal, String changes, Integer postId) {
        UserEntity userEntity = userRepository.findByEmail(userPrincipal.getEmail()).get();
        LogEntity log = LogEntity.builder()
                .title(title)
                .changes(changes)
                .idOfChangedPost(postId)
                .nameOfChanger(userEntity.getLastName() + " " + userEntity.getFirstName())
                .build();
        logRepository.save(log);
    }

    private static <T> T getArgumentFromArrayByClassType(Object[] arguments, Class<T> clazz) {
        return (T) Arrays.stream(arguments)
                .filter(clazz::isInstance)
                .findFirst().orElseThrow(() -> new NoSuchElementException("Unable to find argument"));
    }

    private static Integer getPostIdFromObjectInArray(Object[] arguments) {
        return Arrays.stream(arguments)
                .filter(obj -> obj instanceof PostSaveFromUserDTO)
                .map(obj -> ((PostSaveFromUserDTO) obj).getId())
                .findFirst().orElseThrow(() -> new NoSuchElementException("Unable find argument"));
    }
}