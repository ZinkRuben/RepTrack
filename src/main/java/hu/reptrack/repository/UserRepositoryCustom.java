package hu.reptrack.repository;

/** A UserRepository saját (nem származtatott) műveletei. */
public interface UserRepositoryCustom {

    /**
     * Egy fiók végleges törlése minden adatával (UC14, és az adminisztrátori törlés, UC13).
     *
     * @return igaz, ha a fiók létezett és törlődött
     */
    boolean deleteAccountWithAllData(Long userId);
}
