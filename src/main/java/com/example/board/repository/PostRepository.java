package com.example.board.repository;

import com.example.board.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

//    @Modifying
//    @Query("update Post p set p.viewCount = p.viewCount + 1 where p.id = :id")
//    int increaseViewCount(@Param("id") Long id);
//
//    @Modifying
//    @Query("update Post p set p.viewCount = :count where p.id = :id")
//    void updateViewCount(@Param("id") Long id, @Param("count") Long count);

    @Modifying
    @Query("update Post p set p.viewCount = p.viewCount + :count where p.id = :id")
    int increaseViewCountBy(@Param("id") long id, @Param("count") long count);

    @Modifying
    @Query("update Post p set p.viewCount = p.viewCount + 1 where p.id = :id")
    int directIncreaseViewCount(@Param("id") Long id);
}